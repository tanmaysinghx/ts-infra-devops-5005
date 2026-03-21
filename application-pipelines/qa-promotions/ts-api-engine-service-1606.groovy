pipeline {
    agent any

    environment {
        APP_NAME = "ts-api-engine-service-1606"
        REGISTRY = "tanmaysinghx" // Your Docker Hub ID
        DEPLOY_ENV = "qa"
        // By default, we deploy the 'qa-latest' image tagged by the Build pipeline
        IMAGE_TAG = "qa-latest" 
        DOCKERHUB_CREDS = credentials('DOCKERHUB_CREDS') 
    }

    stages {
        stage('Checkout Infra') {
            steps {
                echo "Preparing Infrastructure Repository..."
                // Check out the infra repo to get the vault script and encrypted secrets
                checkout([$class: 'GitSCM', 
                    branches: [[name: '*/main']], 
                    userRemoteConfigs: [[url: "https://github.com/tanmaysinghx/ts-infra-devops-5005.git"]]
                ])
            }
        }

        stage('Pull Image') {
            steps {
                echo "Pulling newest image from Docker Hub..."
                script {
                    sh 'echo $DOCKERHUB_CREDS_PSW | docker login -u $DOCKERHUB_CREDS_USR --password-stdin'
                    sh "docker pull ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}"
                }
            }
        }

        stage('Decrypt Secrets') {
            steps {
                echo "Decrypting QA secrets..."
                withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
                    sh "node scripts/vault.js decrypt environments/${env.DEPLOY_ENV}/configs/${env.APP_NAME}/.env.enc ${VAULT_PWD}"
                }
            }
        }

        stage('Deploy Container') {
            steps {
                script {
                    echo "Deploying application to VPS..."
                    // Since Jenkins is on the same VPS, we map the local workspace decrypted file directly into the container
                    def secretPath = "${WORKSPACE}/environments/${env.DEPLOY_ENV}/configs/${env.APP_NAME}/.env"
                    
                    sh """
                        docker stop ${env.APP_NAME}-${env.DEPLOY_ENV} || true
                        docker rm ${env.APP_NAME}-${env.DEPLOY_ENV} || true
                        docker run -d \\
                            --name ${env.APP_NAME}-${env.DEPLOY_ENV} \\
                            -p 1606:1606 \\
                            -v ${secretPath}:/usr/src/app/.env \\
                            --restart unless-stopped \\
                            ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}
                    """
                }
            }
        }

        stage('Cleanup') {
            steps {
                echo "Cleaning up local images..."
                // Note: We leave the decrypted .env file intact in the workspace 
                // so the container can still read it if Docker automatically restarts it.
                sh "docker image prune -f"
                sh "docker logout"
            }
        }
    }

    post {
        success {
            echo "Successfully deployed ${env.APP_NAME} to QA!"
        }
        failure {
            echo "Deployment failed! Check the logs."
        }
    }
}
