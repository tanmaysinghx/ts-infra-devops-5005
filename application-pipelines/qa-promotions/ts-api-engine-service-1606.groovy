pipeline {
    agent any

    environment {
        APP_NAME = "ts-api-engine-service-1606"
        REGISTRY = "tanmaysinghx" // Your Docker Hub ID
    environment {
        APP_NAME = "ts-api-engine-service-1606"
        REGISTRY = "tanmaysinghx" // Your Docker Hub ID
        DOCKERHUB_CREDS = credentials('dockerhub-creds') 
        // Detected automatically below
        DEPLOY_ENV = ""
        TARGET_TAG = ""
    }

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Target Docker image tag. "latest" automatically resolves to environment-latest.')
        booleanParam(name: 'RESTART_SERVER_ONLY', defaultValue: false, description: 'Check to simply restart the existing VPS container (skips pulling new images & secrets fallback)')
    }

    stages {
        stage('Initialize Environment') {
            steps {
                script {
                    def branch = env.BRANCH_NAME ?: "main" 
                    env.DEPLOY_ENV = (branch == 'main' || branch == 'master') ? 'prod' : (branch == 'qa' ? 'qa' : 'dev')
                    
                    env.TARGET_TAG = (params.IMAGE_TAG == 'latest') ? "${env.DEPLOY_ENV}-latest" : params.IMAGE_TAG
                    
                    echo "Target Environment: ${env.DEPLOY_ENV}"
                    echo "Target Image Tag: ${env.TARGET_TAG}"
                }
            }
        }

        stage('Checkout Infra') {
            when {
                expression { return !params.RESTART_SERVER_ONLY }
            }
            steps {
                echo "Preparing Infrastructure Repository for ${env.DEPLOY_ENV}..."
                checkout([$class: 'GitSCM', 
                    branches: [[name: '*/main']], 
                    userRemoteConfigs: [[url: "https://github.com/tanmaysinghx/ts-infra-devops-5005.git"]]
                ])
            }
        }

        stage('Pull Image') {
            when {
                expression { return !params.RESTART_SERVER_ONLY }
            }
            steps {
                echo "Pulling ${env.TARGET_TAG} image from Docker Hub..."
                script {
                    sh 'echo $DOCKERHUB_CREDS_PSW | docker login -u $DOCKERHUB_CREDS_USR --password-stdin'
                    sh "docker pull ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}"
                }
            }
        }

        stage('Decrypt Secrets') {
            when {
                expression { return !params.RESTART_SERVER_ONLY }
            }
            steps {
                echo "Decrypting ${env.DEPLOY_ENV} secrets..."
                withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
                    sh "node scripts/vault.js decrypt environments/${env.DEPLOY_ENV}/configs/${env.APP_NAME}/.env.enc ${VAULT_PWD}"
                }
            }
        }

        stage('Deploy Container') {
            when {
                expression { return !params.RESTART_SERVER_ONLY }
            }
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
                            ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}
                    """
                }
            }
        }

        stage('Restart Server Only') {
            when {
                expression { return params.RESTART_SERVER_ONLY }
            }
            steps {
                echo "Restarting the existing container..."
                sh "docker restart ${env.APP_NAME}-${env.DEPLOY_ENV}"
            }
        }

        stage('Cleanup') {
            when {
                expression { return !params.RESTART_SERVER_ONLY }
            }
            steps {
                echo "Cleaning up local images..."
                sh "docker image prune -f"
                sh "docker logout"
            }
        }
    }

    post {
        success {
            echo "Successfully deployed/restarted ${env.APP_NAME} to QA!"
        }
        failure {
            echo "Pipeline failed! Check the logs."
        }
    }
}
