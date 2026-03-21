pipeline {
    agent any

    environment {
        APP_NAME = "ts-api-engine-service-1606"
        REGISTRY = "docker.io/tanmaysinghx"
    }

    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['dev', 'qa', 'prod'], description: 'Target environment')
        string(name: 'IMAGE_TAG', defaultValue: 'latest', description: 'Docker image version')
    }

    stages {
        stage('Checkout') {
            steps {
                echo "🚀 Preparing ${env.APP_NAME}..."
                dir(env.APP_NAME) {
                    checkout([$class: 'GitSCM', 
                        branches: [[name: '*/main']], 
                        userRemoteConfigs: [[url: "https://github.com/tanmaysinghx/${env.APP_NAME}.git"]]
                    ])
                }
            }
        }

        stage('Build Application') {
            steps {
                dir(env.APP_NAME) {
                    echo "📦 Running application build..."
                    // If Node, this runs tsc. If Spring, this would be mvn package.
                    // For now, favoring Docker-managed builds to avoid host dependencies.
                    sh 'echo "Application build logic executed"'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir(env.APP_NAME) {
                    echo "🐳 Building: ${env.REGISTRY}/${env.APP_NAME}:${params.IMAGE_TAG}"
                    sh "docker build -t ${env.REGISTRY}/${env.APP_NAME}:${params.IMAGE_TAG} ."
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                echo "📤 Publishing image..."
                // sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${params.IMAGE_TAG}"
                sh 'echo "Push simulated"'
            }
        }

        stage('Deploy') {
            steps {
                // 1. Decrypt secrets
                withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
                    dir('ts-infra-devops-5005') {
                        echo "🔑 Decrypting ${params.DEPLOY_ENV} secrets..."
                        sh "node scripts/vault.js decrypt environments/${params.DEPLOY_ENV}/configs/${env.APP_NAME}/.env.enc ${VAULT_PWD}"
                    }
                }

                // 2. Start container
                script {
                    def secretPath = "${WORKSPACE}/ts-infra-devops-5005/environments/${params.DEPLOY_ENV}/configs/${env.APP_NAME}/.env"
                    
                    dir(env.APP_NAME) {
                        echo "🚢 Deploying container..."
                        sh """
                            docker stop ${env.APP_NAME}-${params.DEPLOY_ENV} || true
                            docker rm ${env.APP_NAME}-${params.DEPLOY_ENV} || true
                            docker run -d \
                                --name ${env.APP_NAME}-${params.DEPLOY_ENV} \
                                -p 1606:1606 \
                                -v ${secretPath}:/usr/src/app/.env \
                                ${env.REGISTRY}/${env.APP_NAME}:${params.IMAGE_TAG}
                        """
                    }
                }
            }
        }

        stage('Cleanup') {
            steps {
                echo "🧹 Post-deployment cleanup..."
                sh 'docker image prune -f'
            }
        }
    }

    post {
        always {
            echo "🏁 Pipeline execution finished."
        }
    }
}