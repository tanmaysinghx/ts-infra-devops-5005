pipeline {
    agent any

    environment {
        APP_NAME = "ts-api-engine-service-1606"
        REGISTRY = "tanmaysinghx" // Your Docker Hub ID
        DOCKER_CREDS = credentials('dockerhub-creds') 
    }

    stages {
        stage('⚙️ Initialize') {
            steps {
                script {
                    // Detect Environment
                    def branch = env.BRANCH_NAME ?: "main" 
                    env.DEPLOY_ENV = (branch == 'main' || branch == 'master') ? 'prod' : (branch == 'qa' ? 'qa' : 'dev')

                    // Generate Version Tag
                    def shortSha = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.IMAGE_TAG = "build-${env.BUILD_NUMBER}-${shortSha}"
                }
            }
        }

        stage('📂 Checkout') {
            steps {
                dir(env.APP_NAME) {
                    git branch: 'main', 
                        url: "https://github.com/tanmaysinghx/${env.APP_NAME}.git",
                        credentialsId: 'github-token'
                }
            }
        }

        stage('📦 Build App') {
            steps {
                dir(env.APP_NAME) {
                    echo "Running Node/Spring build inside Docker..."
                }
            }
        }

        stage('🐳 Docker Build') {
            steps {
                dir(env.APP_NAME) {
                    sh "docker build -t ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG} ."
                    sh "docker tag ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG} ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                }
            }
        }

        stage('📤 Push to Hub') {
            steps {
                script {
                    sh "echo ${DOCKER_CREDS_PSW} | docker login -u ${DOCKER_CREDS_USR} --password-stdin"
                    sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}"
                    sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                }
            }
        }

        stage('🧹 Cleanup') {
            steps {
                sh "docker rmi ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG} || true"
                sh "docker logout"
            }
        }
    }

    post {
        success {
            echo "✅ Successfully built and pushed ${env.APP_NAME}:${env.IMAGE_TAG}"
        }
    }
}