pipeline {
    agent any

    environment {
        APP_NAME = "ts-api-engine-service-1606"
        REGISTRY = "docker.io/tanmaysinghx"
        // Inferred variables (initially empty, set in the first stage)
        DEPLOY_ENV = ""
        IMAGE_TAG = ""
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    // 1. Detect Environment based on Branch Name
                    // Note: 'env.BRANCH_NAME' requires Multi-branch pipeline or specific Git plugins
                    def branch = env.BRANCH_NAME ?: "main" 
                    if (branch == 'main' || branch == 'master') {
                        env.DEPLOY_ENV = 'prod'
                    } else if (branch == 'qa') {
                        env.DEPLOY_ENV = 'qa'
                    } else {
                        env.DEPLOY_ENV = 'dev'
                    }

                    // 2. Generate standard Image Tag (Build Number + Short SHA)
                    def shortSha = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.IMAGE_TAG = "build-${env.BUILD_NUMBER}-${shortSha}"

                    echo "🚀 Automated Initialization:"
                    echo "   - App: ${env.APP_NAME}"
                    echo "   - Branch: ${branch}"
                    echo "   - Target Env: ${env.DEPLOY_ENV}"
                    echo "   - Version Tag: ${env.IMAGE_TAG}"
                }
            }
        }

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
                    sh 'echo "Application build logic executed"'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                dir(env.APP_NAME) {
                    echo "🐳 Building: ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}"
                    sh "docker build -t ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG} ."
                    // Also tag as 'latest' for the specific environment
                    sh "docker tag ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG} ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                echo "📤 Publishing images..."
                // sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}"
                // sh "docker push ${env.REGISTRY}/${env.APP_NAME}:${env.DEPLOY_ENV}-latest"
                sh 'echo "Push simulated"'
            }
        }

        stage('Deploy') {
            steps {
                // 1. Decrypt secrets
                withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
                    dir('ts-infra-devops-5005') {
                        echo "🔑 Decrypting ${env.DEPLOY_ENV} secrets..."
                        sh "node scripts/vault.js decrypt environments/${env.DEPLOY_ENV}/configs/${env.APP_NAME}/.env.enc ${VAULT_PWD}"
                    }
                }

                // 2. Start container
                script {
                    def secretPath = "${WORKSPACE}/ts-infra-devops-5005/environments/${env.DEPLOY_ENV}/configs/${env.APP_NAME}/.env"
                    
                    dir(env.APP_NAME) {
                        echo "🚢 Deploying container for ${env.DEPLOY_ENV}..."
                        sh """
                            docker stop ${env.APP_NAME}-${env.DEPLOY_ENV} || true
                            docker rm ${env.APP_NAME}-${env.DEPLOY_ENV} || true
                            docker run -d \
                                --name ${env.APP_NAME}-${env.DEPLOY_ENV} \
                                -p 1606:1606 \
                                -v ${secretPath}:/usr/src/app/.env \
                                ${env.REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}
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
            echo "🏁 Pipeline execution finished for ${env.APP_NAME}."
        }
    }
}