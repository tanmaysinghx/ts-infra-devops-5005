pipeline {
    agent any

    environment {
        APP_NAME = "portal-sso"
        REGISTRY = "tanmaysinghx"
        GITHUB_REPO = "tanmaysinghx/portal-sso"
        PATH = "${WORKSPACE}/bin:${env.PATH}"
    }

    parameters {
        string(name: 'RELEASE_TAG', defaultValue: 'latest', description: 'Target GitHub Release tag (e.g. "latest", "v25.0.1"). "latest" downloads the most recent published release.')
        booleanParam(name: 'DEPLOY_TO_DEV', defaultValue: true, description: 'Deploy the container to Dev environment')
        string(name: 'DEV_PORT', defaultValue: '8090', description: 'Host port to bind for Dev container instance (defaults to 8090)')
        booleanParam(name: 'PUSH_TO_DOCKERHUB', defaultValue: true, description: 'Push built image to Docker Hub registry')
        string(name: 'DOCKERHUB_CRED_ID', defaultValue: 'dockerhub-creds', description: 'Jenkins Credential ID for Docker Hub (Username with password)')
    }

    stages {
        stage('Initialize & Ensure Docker CLI') {
            steps {
                script {
                    echo "Checking Docker CLI availability on Jenkins agent..."
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

                        if [ -S /var/run/docker.sock ]; then
                            echo "✅ Docker socket /var/run/docker.sock detected."
                        else
                            echo "⚠️ Warning: /var/run/docker.sock is not visible in this container."
                        fi
                    """
                }
            }
        }

        stage('Resolve Release Tag') {
            steps {
                script {
                    env.DEPLOY_ENV = 'dev'
                    def requestedTag = params.RELEASE_TAG?.trim() ?: 'latest'

                    if (requestedTag == 'latest') {
                        echo "Querying GitHub for the latest release tag of ${env.GITHUB_REPO}..."
                        try {
                            def tagFromApi = sh(
                                script: """
                                    curl -fsSL --connect-timeout 10 https://api.github.com/repos/${env.GITHUB_REPO}/releases/latest 2>/dev/null \
                                        | grep -o '"tag_name": *"[^"]*"' | head -n 1 | cut -d'"' -f4
                                """,
                                returnStdout: true
                            ).trim()

                            if (tagFromApi && tagFromApi.startsWith("v")) {
                                env.TARGET_TAG = tagFromApi
                            } else {
                                def tagFromRedirect = sh(
                                    script: "curl -sIL -o /dev/null -w '%{url_effective}' https://github.com/${env.GITHUB_REPO}/releases/latest | awk -F'/' '{print \$NF}'",
                                    returnStdout: true
                                ).trim()
                                env.TARGET_TAG = (tagFromRedirect && tagFromRedirect != 'latest') ? tagFromRedirect : "v25.0.${env.BUILD_NUMBER}"
                            }
                        } catch (Exception e) {
                            echo "Warning: could not resolve latest tag from API: ${e.message}. Using build number tag."
                            env.TARGET_TAG = "v25.0.${env.BUILD_NUMBER}"
                        }
                    } else {
                        env.TARGET_TAG = requestedTag
                    }

                    echo "========================================================="
                    echo " Target Application : ${env.APP_NAME}"
                    echo " Target Version Tag : ${env.TARGET_TAG}"
                    echo " Target Environment : ${env.DEPLOY_ENV}"
                    echo " Target Port        : ${params.DEV_PORT}"
                    echo "========================================================="
                }
            }
        }

        stage('Download GitHub Release JAR') {
            steps {
                script {
                    echo "Fetching pre-built standalone executable JAR from GitHub Releases..."
                    sh """
                        rm -rf portal-dist && mkdir -p portal-dist
                        cd portal-dist

                        if [ "${params.RELEASE_TAG}" = "latest" ]; then
                            DOWNLOAD_URL="https://github.com/${env.GITHUB_REPO}/releases/latest/download/portal-sso.jar"
                            CHECKSUM_URL="https://github.com/${env.GITHUB_REPO}/releases/latest/download/portal-sso.jar.sha256"
                        else
                            DOWNLOAD_URL="https://github.com/${env.GITHUB_REPO}/releases/download/${env.TARGET_TAG}/portal-sso.jar"
                            CHECKSUM_URL="https://github.com/${env.GITHUB_REPO}/releases/download/${env.TARGET_TAG}/portal-sso.jar.sha256"
                        fi

                        echo "Downloading: \${DOWNLOAD_URL}"
                        curl -fSL --progress-bar -o portal-sso.jar "\${DOWNLOAD_URL}"
                        curl -fSL -o portal-sso.jar.sha256 "\${CHECKSUM_URL}" || true

                        if [ -f portal-sso.jar.sha256 ]; then
                            echo "Verifying SHA256 Checksum..."
                            sha256sum -c portal-sso.jar.sha256 || echo "Checksum verification non-blocking"
                        fi

                        ls -lh portal-sso.jar
                    """
                }
            }
        }

        stage('Build Lightweight Docker Image') {
            steps {
                script {
                    echo "Packaging pre-built JAR into slim JRE 25 Docker image configured on port 8090..."
                    sh """
                        cd portal-dist

                        cat << 'DOCKER_EOF' > Dockerfile
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY portal-sso.jar app.jar
EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \\
    CMD curl -fsS http://localhost:8090/actuator/health/readiness || exit 1
ENV SERVER_PORT=8090
ENV PORTAL_HOME=/app/.portal-sso
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java \$JAVA_OPTS -jar /app/app.jar"]
DOCKER_EOF

                        docker build -t ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG} .
                        docker tag ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG} ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest
                        docker tag ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG} ${env.REGISTRY}/${env.APP_NAME}:latest
                    """
                }
            }
        }

        stage('Push to Docker Hub') {
            when {
                expression { return params.PUSH_TO_DOCKERHUB == true }
            }
            steps {
                script {
                    def credId = params.DOCKERHUB_CRED_ID?.trim() ?: 'dockerhub-creds'
                    echo "Checking Docker Hub authentication using credential '${credId}'..."
                    try {
                        withCredentials([usernamePassword(credentialsId: credId, usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                            sh 'echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin'
                            echo "Pushing images (${env.TARGET_TAG}, ${env.DEPLOY_ENV}-latest, latest)..."
                            sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}"
                            sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                            sh "docker push ${env.REGISTRY}/${env.APP_NAME}:latest"
                        }
                    } catch (Exception e) {
                        echo "⚠️ Warning: Could not push to Docker Hub with credential '${credId}': ${e.message}"
                        echo "Ensure credential '${credId}' (Username with password) is created in Jenkins Credentials."
                        echo "Continuing to local container deployment because the image is already built locally."
                    }
                }
            }
        }

        stage('Deploy to Dev Container') {
            when {
                expression { return params.DEPLOY_TO_DEV == true }
            }
            steps {
                script {
                    echo "Deploying ${env.APP_NAME} to Dev environment on port ${params.DEV_PORT}..."
                    def secretPath = "${WORKSPACE}/environments/dev/configs/${env.APP_NAME}/.env"
                    def secretEncPath = "${WORKSPACE}/environments/dev/configs/${env.APP_NAME}/.env.enc"

                    if (fileExists(secretEncPath)) {
                        try {
                            withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
                                sh "docker run --rm -v \"\${WORKSPACE}:/workspace\" -w /workspace node:20-alpine node scripts/vault.js decrypt environments/dev/configs/${env.APP_NAME}/.env.enc \"\$VAULT_PWD\""
                            }
                        } catch (Exception e) {
                            echo "Notice: Could not decrypt vault secrets (${e.message}). Proceeding with default environment."
                        }
                    }

                    def envFileOption = fileExists(secretPath) ? "--env-file ${secretPath}" : ""

                    sh """
                        docker network create ts-app-network || true
                        docker stop ${env.APP_NAME}-${env.DEPLOY_ENV} || true
                        docker rm ${env.APP_NAME}-${env.DEPLOY_ENV} || true

                        docker run -d \\
                            --name ${env.APP_NAME}-${env.DEPLOY_ENV} \\
                            --network ts-app-network \\
                            -p ${params.DEV_PORT}:8090 \\
                            -e SERVER_PORT=8090 \\
                            -e PORTAL_HOME=/app/.portal-sso \\
                            -e ISSUER_URL=https://portal.tanmaysinghx.com \\
                            ${envFileOption} \\
                            -v ${env.APP_NAME}-${env.DEPLOY_ENV}-data:/app/.portal-sso \\
                            --restart unless-stopped \\
                            ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}
                    """
                }
            }
        }

        stage('Cleanup') {
            steps {
                script {
                    echo "Cleaning up build artifacts and temporary containers..."
                    sh "rm -rf portal-dist || true"
                    sh "docker image prune -f || true"
                    sh "docker logout || true"
                }
            }
        }
    }

    post {
        success {
            echo "✅ Successfully deployed ${env.APP_NAME}:${env.TARGET_TAG} on port ${params.DEV_PORT}!"
        }
        failure {
            echo "❌ Pipeline failed! Please check console output for details."
        }
    }
}
