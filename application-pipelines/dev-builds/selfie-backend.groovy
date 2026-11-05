// selfie-backend (Rust/Axum API for tanmaysinghx.com blog reactions and comments):
// builds from source, runs the test suite, pushes to Docker Hub and deploys the dev container
// behind nginx (selfie.tanmaysinghx.com -> 127.0.0.1:8099).
//
// Secrets: the full .env comes from the Jenkins "Secret file" credential ENV_CRED_ID, or from
// environments/dev/configs/selfie-backend/.env.enc (vault) when present. See .env.example there.
pipeline {
    agent any

    environment {
        APP_NAME = "selfie-backend"
        IMAGE = "tanmaysinghx/selfie-backend"
        GITHUB_REPO = "tanmaysinghx/selfie-portfolio"
        CONTEXT_DIR = "backend"
        CONTAINER_PORT = "8080"
        PATH = "${WORKSPACE}/bin:${env.PATH}"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '5', daysToKeepStr: '15'))
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'GIT_REF', defaultValue: 'main', description: 'Branch or tag of tanmaysinghx/selfie-portfolio to build')
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Run the backend test suite (cargo test) before building the image')
        booleanParam(name: 'DEPLOY_TO_DEV', defaultValue: true, description: 'Deploy the container to Dev environment')
        string(name: 'DEV_PORT', defaultValue: '8099', description: 'Host port (bound to 127.0.0.1; nginx proxies selfie.tanmaysinghx.com to it)')
        string(name: 'MEMORY_LIMIT', defaultValue: '128m', description: 'Container memory limit (the API idles at ~10 MB)')
        booleanParam(name: 'PUSH_TO_DOCKERHUB', defaultValue: true, description: 'Push built image to Docker Hub registry')
        string(name: 'DOCKERHUB_CRED_ID', defaultValue: 'dockerhub-creds', description: 'Jenkins Credential ID for Docker Hub (Username with password)')
        string(name: 'ENV_CRED_ID', defaultValue: 'selfie-backend-dev-env', description: 'Jenkins Secret file credential holding the app .env')
    }

    stages {
        stage('Initialize & Ensure Docker CLI') {
            steps {
                sh '''
                    mkdir -p "$WORKSPACE/bin"
                    if ! command -v docker >/dev/null 2>&1; then
                        if [ -x /usr/bin/docker ]; then
                            ln -sf /usr/bin/docker "$WORKSPACE/bin/docker"
                        elif [ -x /usr/local/bin/docker ]; then
                            ln -sf /usr/local/bin/docker "$WORKSPACE/bin/docker"
                        else
                            case "$(uname -m)" in aarch64|arm64) ARCH=aarch64 ;; *) ARCH=x86_64 ;; esac
                            curl -fsSL "https://download.docker.com/linux/static/stable/$ARCH/docker-27.5.1.tgz" -o /tmp/docker.tgz
                            tar -xz -C /tmp -f /tmp/docker.tgz docker/docker
                            mv /tmp/docker/docker "$WORKSPACE/bin/docker" && chmod +x "$WORKSPACE/bin/docker"
                            rm -rf /tmp/docker /tmp/docker.tgz
                        fi
                    fi
                    docker --version
                    [ -S /var/run/docker.sock ] || echo "⚠️ /var/run/docker.sock is not visible in this agent"
                '''
            }
        }

        stage('Checkout Source') {
            steps {
                dir('src') {
                    deleteDir()
                    checkout([$class: 'GitSCM',
                              branches: [[name: params.GIT_REF]],
                              userRemoteConfigs: [[url: "https://github.com/${env.GITHUB_REPO}.git"]],
                              extensions: [[$class: 'CloneOption', depth: 1, shallow: true, noTags: false]]])
                    script {
                        env.DEPLOY_ENV = 'dev'
                        env.TARGET_TAG = sh(script: 'git rev-parse --short=10 HEAD', returnStdout: true).trim()
                        echo "========================================================="
                        echo " Target Application : ${env.APP_NAME}"
                        echo " Source             : ${env.GITHUB_REPO}@${params.GIT_REF} (${env.TARGET_TAG})"
                        echo " Target Environment : ${env.DEPLOY_ENV}"
                        echo " Target Port        : 127.0.0.1:${params.DEV_PORT}"
                        echo "========================================================="
                    }
                }
            }
        }

        stage('Test') {
            when { expression { return params.RUN_TESTS } }
            steps {
                dir("src/${env.CONTEXT_DIR}") {
                    // Unit + HTTP tests; database tests skip themselves (no TEST_DATABASE_URL in CI).
                    sh 'docker build --target test -t ${IMAGE}:test-${TARGET_TAG} .'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir("src/${env.CONTEXT_DIR}") {
                    sh '''
                        docker build -t ${IMAGE}:${TARGET_TAG} .
                        docker tag ${IMAGE}:${TARGET_TAG} ${IMAGE}:${DEPLOY_ENV}-latest
                        docker tag ${IMAGE}:${TARGET_TAG} ${IMAGE}:latest
                    '''
                }
            }
        }

        stage('Push to Docker Hub') {
            when { expression { return params.PUSH_TO_DOCKERHUB } }
            steps {
                script {
                    def credId = params.DOCKERHUB_CRED_ID?.trim() ?: 'dockerhub-creds'
                    try {
                        withCredentials([usernamePassword(credentialsId: credId, usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                            sh 'echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin'
                            sh 'docker push ${IMAGE}:${TARGET_TAG} && docker push ${IMAGE}:${DEPLOY_ENV}-latest && docker push ${IMAGE}:latest'
                        }
                    } catch (Exception e) {
                        echo "⚠️ Could not push to Docker Hub with '${credId}': ${e.message}. Continuing with the local image."
                    }
                }
            }
        }

        stage('Prepare Environment') {
            when { expression { return params.DEPLOY_TO_DEV } }
            steps {
                script {
                    def encPath = "environments/dev/configs/${env.APP_NAME}/.env.enc"
                    if (fileExists(encPath)) {
                        withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
                            sh "docker run --rm -v \"\$WORKSPACE:/workspace\" -w /workspace node:20-alpine node scripts/vault.js decrypt ${encPath} \"\$VAULT_PWD\""
                        }
                        sh "cp environments/dev/configs/${env.APP_NAME}/.env app.env && rm -f environments/dev/configs/${env.APP_NAME}/.env"
                    } else {
                        withCredentials([file(credentialsId: params.ENV_CRED_ID, variable: 'APP_ENV_FILE')]) {
                            sh 'cp "$APP_ENV_FILE" app.env'
                        }
                    }
                    sh 'chmod 600 app.env && grep -c "=" app.env | xargs -I{} echo "Loaded {} settings"'
                }
            }
        }

        stage('Deploy to Dev Container') {
            when { expression { return params.DEPLOY_TO_DEV } }
            steps {
                sh '''
                    CONTAINER=${APP_NAME}-${DEPLOY_ENV}
                    docker network create ts-app-network 2>/dev/null || true
                    docker stop $CONTAINER 2>/dev/null || true
                    docker rm $CONTAINER 2>/dev/null || true

                    docker run -d \
                        --name $CONTAINER \
                        --network ts-app-network \
                        -p 127.0.0.1:${DEV_PORT}:${CONTAINER_PORT} \
                        --memory ${MEMORY_LIMIT} \
                        --env-file app.env \
                        -e HOST=0.0.0.0 \
                        -e PORT=${CONTAINER_PORT} \
                        --read-only \
                        --cap-drop ALL \
                        --security-opt no-new-privileges \
                        --log-opt max-size=20m --log-opt max-file=3 \
                        --restart unless-stopped \
                        ${IMAGE}:${TARGET_TAG}
                '''
            }
        }

        stage('Health Check') {
            when { expression { return params.DEPLOY_TO_DEV } }
            steps {
                sh '''
                    CONTAINER=${APP_NAME}-${DEPLOY_ENV}
                    for i in $(seq 1 24); do
                        STATUS=$(docker inspect -f '{{.State.Health.Status}}' $CONTAINER 2>/dev/null || echo missing)
                        echo "[$i/24] $CONTAINER: $STATUS"
                        if [ "$STATUS" = "healthy" ]; then
                            # Liveness is up; readiness also needs MySQL + migrations. Report it, don't fail on it:
                            # the API serves 503s gracefully and keeps retrying the database.
                            docker run --rm --network ts-app-network curlimages/curl:8.11.1 -fsS http://$CONTAINER:${CONTAINER_PORT}/health/ready \
                                && echo "✅ database ready" || echo "⚠️ API is up but the database is not ready yet"
                            exit 0
                        fi
                        if [ "$STATUS" = "unhealthy" ] || [ "$(docker inspect -f '{{.State.Running}}' $CONTAINER 2>/dev/null)" != "true" ]; then
                            break
                        fi
                        sleep 5
                    done
                    echo "❌ $CONTAINER did not become healthy. Last logs:"
                    docker logs --tail 80 $CONTAINER || true
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            sh 'rm -f app.env environments/dev/configs/${APP_NAME}/.env || true'
            sh 'docker image rm ${IMAGE}:test-${TARGET_TAG} 2>/dev/null || true; docker image prune -f || true; docker logout || true'
        }
        success {
            echo "✅ Deployed ${env.APP_NAME}:${env.TARGET_TAG} on 127.0.0.1:${params.DEV_PORT} (https://selfie.tanmaysinghx.com)"
        }
        failure {
            echo "❌ Pipeline failed! Please check console output for details."
        }
    }
}
