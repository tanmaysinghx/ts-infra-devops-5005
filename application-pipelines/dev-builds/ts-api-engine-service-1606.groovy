pipeline {
  agent any

  parameters {
    choice(name: 'BUILD_ENV', choices: ['dev', 'qa', 'prod'], description: 'Select deployment environment')
    string(name: 'DOCKER_TAG', defaultValue: 'latest', description: 'Docker image tag')
  }

  stages {
    stage('Build Generic Image') {
      steps {
        dir('ts-api-engine-service-1606') {
            // No host-level 'npm install' or 'npm run build' needed. 
            // Docker handles this inside the image.
            sh "docker build -t ts-api-engine-service-1606:${params.DOCKER_TAG} ."
            sh "docker push ts-api-engine-service-1606:${params.DOCKER_TAG} || echo 'Push failed - check credentials'"
        }
      }
    }

    stage('Deploy with Secrets') {
      steps {
        // Unlock secrets using the custom vault script
        withCredentials([string(credentialsId: 'infra-vault-pwd', variable: 'VAULT_PWD')]) {
          dir('ts-infra-devops-5005') {
            sh "node scripts/vault.js decrypt environments/${params.BUILD_ENV}/configs/ts-api-engine-service-1606/.env.enc ${VAULT_PWD}"
          }
        }

        dir('ts-api-engine-service-1606') {
            echo "Deploying to ${params.BUILD_ENV} using runtime secret injection..."
            
            script {
                // 1. Get the absolute path to the secrets in infra repo
                def secretPath = "${WORKSPACE}/ts-infra-devops-5005/environments/${params.BUILD_ENV}/configs/ts-api-engine-service-1606/.env"
                
                // 2. Start the container, mounting the secrets from the build server
                sh """
                  docker stop ts-api-engine-${params.BUILD_ENV} || true
                  docker rm ts-api-engine-${params.BUILD_ENV} || true
                  docker run -d \
                    --name ts-api-engine-${params.BUILD_ENV} \
                    -p 1606:1606 \
                    -v ${secretPath}:/usr/src/app/.env \
                    ts-api-engine-service-1606:${params.DOCKER_TAG}
                """
            }
        }
      }
    }
  }
}