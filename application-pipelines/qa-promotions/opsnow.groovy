pipeline {
    agent any

    environment {
        APP_NAME = "opsnow"
        REGISTRY = "tanmaysinghx"
        PATH = "${WORKSPACE}/bin:${env.PATH}"
    }

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Target Docker image tag or release tag to promote (defaults to "latest", which resolves to dev-latest)')
        string(name: 'QA_PORT', defaultValue: '8085', description: 'Host port to bind for QA container instance (defaults to 8085)')
        string(name: 'DOCKERHUB_CRED_ID', defaultValue: 'dockerhub-creds', description: 'Jenkins Credential ID for Docker Hub (Username with password)')
    }

    stages {
        stage('Initialize Environment') {
            steps {
                script {
                    env.DEPLOY_ENV = 'qa'
                    def requestedTag = params.IMAGE_TAG?.trim() ?: 'latest'
                    env.TARGET_TAG = (requestedTag == 'latest') ? "dev-latest" : requestedTag

                    echo "========================================================="
                    echo " QA Promotion Target  : ${env.APP_NAME}:${env.TARGET_TAG}"
                    echo " Target Environment   : ${env.DEPLOY_ENV}"
                    echo " Target Port          : ${params.QA_PORT}"
                    echo "========================================================="

                    sh """
                        mkdir -p "${WORKSPACE}/bin"
                        if command -v docker >/dev/null 2>&1; then
                            echo "✅ Docker CLI found at: \$(which docker)"
                        else
                            echo "Docker CLI not in standard PATH. Searching host paths or installing static binary..."
                            if [ -x /usr/bin/docker ]; then
                                ln -sf /usr/bin/docker "${WORKSPACE}/bin/docker"
                            elif [ -x /usr/local/bin/docker ]; then
                                ln -sf /usr/local/bin/docker "${WORKSPACE}/bin/docker"
                            else
                                echo "Fetching official static Docker client..."
                                ARCH=\$(uname -m)
                                case "\$ARCH" in
                                    x86_64|amd64) DOCKER_ARCH="x86_64" ;;
                                    aarch64|arm64) DOCKER_ARCH="aarch64" ;;
                                    *) DOCKER_ARCH="x86_64" ;;
                                esac
                                curl -fsSL "https://download.docker.com/linux/static/stable/\${DOCKER_ARCH}/docker-27.5.1.tgz" -o /tmp/docker.tgz
                                tar -xz -C /tmp -f /tmp/docker.tgz docker/docker
                                mv /tmp/docker/docker "${WORKSPACE}/bin/docker"
                                chmod +x "${WORKSPACE}/bin/docker"
                                rm -rf /tmp/docker /tmp/docker.tgz
                                echo "Static Docker CLI installed to ${WORKSPACE}/bin/docker"
                            fi
                        fi

                        "${WORKSPACE}/bin/docker" --version || docker --version || true
                    """
                }
            }
        }

        stage('Checkout Infra') {
            steps {
                echo "Checking out ts-infra-devops-5005 repository for ${env.DEPLOY_ENV} configuration..."
                checkout([$class: 'GitSCM',
                    branches: [[name: '*/main']],
                    userRemoteConfigs: [[url: "https://github.com/tanmaysinghx/ts-infra-devops-5005.git"]]
                ])
            }
        }

        stage('Pull & Promote Image') {
            steps {
                echo "Pulling ${env.TARGET_TAG} from Docker Hub and tagging as qa-latest..."
                script {
                    def credId = params.DOCKERHUB_CRED_ID?.trim() ?: 'dockerhub-creds'
                    try {
                        withCredentials([usernamePassword(credentialsId: credId, usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                            sh 'echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin'
                        }
                    } catch (Exception e) {
                        echo "Notice: Proceeding without explicit Docker Hub login (${e.message})."
                    }
                    sh "docker pull ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}"
                    sh "docker tag ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG} ${env.REGISTRY}/${env.APP_NAME}:qa-latest"
                    try {
                        sh "docker push ${env.REGISTRY}/${env.APP_NAME}:qa-latest"
                    } catch (Exception e) {
                        echo "Warning: Could not push qa-latest tag to Docker Hub: ${e.message}"
                    }
                }
            }
        }

        stage('Decrypt Secrets') {
            steps {
                echo "Decrypting ${env.DEPLOY_ENV} secrets using custom vault..."
                script {
                    def secretEncPath = "environments/${env.DEPLOY_ENV}/configs/${env.APP_NAME}/.env.enc"
                    if (fileExists(secretEncPath)) {
                        withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
                            sh 'docker run --rm -v "${WORKSPACE}:/workspace" -w /workspace node:20-alpine node scripts/vault.js decrypt environments/' + env.DEPLOY_ENV + '/configs/' + env.APP_NAME + '/.env.enc "$VAULT_PWD"'
                        }
                    } else {
                        echo "Notice: No encrypted secrets file found at ${secretEncPath}. Proceeding with container defaults."
                    }
                }
            }
        }

        stage('Deploy Container') {
            steps {
                script {
                    echo "Deploying ${env.APP_NAME} container to QA environment on port ${params.QA_PORT}..."
                    def secretPath = "${WORKSPACE}/environments/${env.DEPLOY_ENV}/configs/${env.APP_NAME}/.env"
                    def envOption = fileExists(secretPath) ? "--env-file ${secretPath}" : ""

                    sh """
                        docker network create ts-app-network || true
                        docker stop ${env.APP_NAME}-${env.DEPLOY_ENV} || true
                        docker rm ${env.APP_NAME}-${env.DEPLOY_ENV} || true

                        docker run -d \\
                            --name ${env.APP_NAME}-${env.DEPLOY_ENV} \\
                            --network ts-app-network \\
                            -p ${params.QA_PORT}:8085 \\
                            -e SERVER_PORT=8085 \\
                            -e OPSNOW_AUTH_SSO_PORTAL_URL=https://portal.tanmaysinghx.com/dashboard \\
                            -e OPSNOW_AUTH_SSO_SIGN_IN_URL=https://portal.tanmaysinghx.com/sign-in \\
                            -e OPSNOW_AUTH_SSO_ISSUER=https://portal.tanmaysinghx.com \\
                            ${envOption} \\
                            --restart unless-stopped \\
                            ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}
                    """
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    echo "Validating QA container startup and readiness on port ${params.QA_PORT}..."
                    sh """
                        sleep 10
                        READY=0
                        for i in \$(seq 1 12); do
                            if curl -fsS "http://localhost:${params.QA_PORT}/actuator/health" > /dev/null 2>&1; then
                                echo "✅ OpsNow QA instance is healthy and ready on port ${params.QA_PORT}!"
                                READY=1
                                break
                            fi
                            echo "Waiting for OpsNow QA container to become healthy (attempt \$i/12)..."
                            sleep 5
                        done

                        if [ \$READY -ne 1 ]; then
                            echo "❌ Error: OpsNow QA instance did not become healthy within timeout."
                            docker logs --tail 50 ${env.APP_NAME}-${env.DEPLOY_ENV}
                            exit 1
                        fi
                    """
                }
            }
        }

        stage('Cleanup') {
            steps {
                script {
                    echo "Pruning untagged containers and images..."
                    sh "docker image prune -f || true"
                    sh "docker logout || true"
                }
            }
        }
    }

    post {
        success {
            echo "✅ Successfully promoted and deployed ${env.APP_NAME}:${env.TARGET_TAG} to QA on port ${params.QA_PORT}!"
        }
        failure {
            echo "❌ QA Promotion pipeline failed! Check console output for logs."
        }
    }
}
