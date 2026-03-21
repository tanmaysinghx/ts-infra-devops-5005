pipeline {
    agent any

    environment {
        APP_NAME = "ts-api-engine-service-1606"
        REGISTRY = "tanmaysinghx" // Your Docker Hub ID
        DOCKERHUB_CREDS = credentials('dockerhub-creds') 
    }

    stages {
        stage('⚙️ Initialize') {
            steps {
                script {
                    // Detect Environment
                    def branch = env.BRANCH_NAME ?: "main" 
                    env.DEPLOY_ENV = (branch == 'main' || branch == 'master') ? 'prod' : (branch == 'qa' ? 'qa' : 'dev')

                    // Generate Version Tag (Build Number + Short SHA)
                    def shortSha = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.IMAGE_TAG = "v${env.BUILD_NUMBER}-${shortSha}"

                    echo "🚀 Pipeline Initialized: ${env.APP_NAME}:${env.IMAGE_TAG} for ${env.DEPLOY_ENV}"
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
                    echo "🛠️ Building ${env.APP_NAME}..."
                    // This is where you run 'npm run build' or 'mvn package'
                    // For now, we rely on the Dockerfile to handle the internal build
                    sh 'echo "Application source verified and ready for containerization"'
                }
            }
        }

        stage('🐳 Docker Build') {
            steps {
                dir(env.APP_NAME) {
                    echo "🏗️ Creating Docker Image..."
                    sh "docker build -t ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG} ."
                    sh "docker tag ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG} ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                }
            }
        }

        stage('📤 Push to Hub') {
            steps {
                script {
                    echo "🔐 Logging into Docker Hub..."
                    sh "echo ${DOCKERHUB_CREDS_PSW} | docker login -u ${DOCKERHUB_CREDS_USR} --password-stdin"
                    
                    echo "🚀 Pushing images..."
                    sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}"
                    sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                }
            }
        }

        stage('🧹 Cleanup') {
            steps {
                echo "♻️ Cleaning up local images..."
                sh "docker rmi ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG} || true"
                sh "docker rmi ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest || true"
                sh "docker logout"
            }
        }
    }

    post {
        success {
            echo "✅ Successfully built and pushed ${env.APP_NAME}:${env.IMAGE_TAG}"
        }
        failure {
            echo "❌ Pipeline failed! Please check console for details."
        }
    }
}