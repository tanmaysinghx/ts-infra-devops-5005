pipeline {
    agent any

    environment {
        APP_NAME = "ts-api-gateway-1701"
        REGISTRY = "tanmaysinghx"
        DOCKERHUB_CREDS = credentials('dockerhub-creds') 
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    // Detect Environment
                    def branch = env.BRANCH_NAME ?: "main" 
                    def determinedEnv = (branch == 'main' || branch == 'master') ? 'prod' : (branch == 'qa' ? 'qa' : 'dev')
                    env.DEPLOY_ENV = determinedEnv

                    // Auto-generate incrementing tag and never prompt user
                    env.TARGET_TAG = "v${env.BUILD_NUMBER}"

                    echo "Pipeline Initialized: ${env.APP_NAME}:${env.TARGET_TAG} for ${env.DEPLOY_ENV}"
                }
                dir(env.APP_NAME) {
                    git branch: 'main', 
                        url: "https://github.com/tanmaysinghx/ts-api-gateway-1701.git",
                        credentialsId: 'github-token'
                }
            }
        }

        stage('Build Application') {
            steps {
                dir(env.APP_NAME) {
                    echo "The application will be built directly inside the container during the Docker Build stage."
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir(env.APP_NAME) {
                    echo "Creating Docker Image..."
                    sh "docker build -t ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG} ."
                    sh "docker tag ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG} ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                script {
                    echo "Logging into Docker Hub (securely)..."
                    // Using single quotes so the shell resolves the ENV var instead of Jenkins parsing it (prevents credential leaks)
                    sh 'echo $DOCKERHUB_CREDS_PSW | docker login -u $DOCKERHUB_CREDS_USR --password-stdin'
                    
                    echo "Pushing images..."
                    sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}"
                    sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                }
            }
        }

        stage('Cleanup') {
            steps {
                echo "Cleaning up local images..."
                sh "docker rmi ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG} || true"
                sh "docker rmi ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest || true"
                sh "docker logout"
            }
        }
    }

    post {
        success {
            echo "Successfully built and pushed ${env.APP_NAME}:${env.TARGET_TAG}"
        }
        failure {
            echo "Pipeline failed! Please check console for details."
        }
    }
}
