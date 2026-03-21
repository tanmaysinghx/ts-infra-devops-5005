pipeline {
    agent any

    environment {
        APP_NAME = "ts-api-engine-service-1606"
        REGISTRY = "tanmaysinghx" // Your Docker Hub ID
        DOCKERHUB_CREDS = credentials('dockerhub-creds') 
    }

    parameters {
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Target Docker image tag. "latest" automatically resolves to environment-latest.')
    }

    stages {
        stage('Initialize Environment') {
            steps {
                script {
                    def branch = env.BRANCH_NAME ?: "main" 
                    def determinedEnv = (branch == 'main' || branch == 'master') ? 'prod' : (branch == 'qa' ? 'qa' : 'dev')
                    env.DEPLOY_ENV = determinedEnv
                    
                    // Safely handle null params on first Jenkins run
                    def requestedTag = params.IMAGE_TAG ?: 'latest'
                    env.TARGET_TAG = (requestedTag == 'latest') ? "${determinedEnv}-latest" : requestedTag
                    
                    echo "Target Environment: ${env.DEPLOY_ENV}"
                    echo "Target Image Tag: ${env.TARGET_TAG}"
                }
            }
        }

        stage('Checkout Infra') {
            steps {
                echo "Preparing Infrastructure Repository for ${env.DEPLOY_ENV}..."
                checkout([$class: 'GitSCM', 
                    branches: [[name: '*/main']], 
                    userRemoteConfigs: [[url: "https://github.com/tanmaysinghx/ts-infra-devops-5005.git"]]
                ])
            }
        }

        stage('Pull Image') {
            steps {
                echo "Pulling ${env.TARGET_TAG} image from Docker Hub..."
                script {
                    sh 'echo $DOCKERHUB_CREDS_PSW | docker login -u $DOCKERHUB_CREDS_USR --password-stdin'
                    sh "docker pull ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}"
                }
            }
        }

        stage('Decrypt Secrets') {
            steps {
                echo "Decrypting ${env.DEPLOY_ENV} secrets..."
                withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
                    // Use a temporary docker container to avoid requiring 'node' on the host VPS.
                    // Single quotes are used to let the bash shell resolve VAULT_PWD, preventing Jenkins security warnings.
                    sh 'docker run --rm -v "${WORKSPACE}:/workspace" -w /workspace node:20-alpine node scripts/vault.js decrypt environments/' + env.DEPLOY_ENV + '/configs/' + env.APP_NAME + '/.env.enc "$VAULT_PWD"'
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
                            -e TARGET_ENV=${env.DEPLOY_ENV} \\
                            -v ${secretPath}:/usr/src/app/.env \\
                            --restart unless-stopped \\
                            ${env.REGISTRY}/${env.APP_NAME}:${env.TARGET_TAG}
                    """
                }
            }
        }

        stage('Cleanup') {
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
