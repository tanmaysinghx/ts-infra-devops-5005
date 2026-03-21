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
            sh 'npm install'
            sh 'npm run build'
            // Build a generic image (notice no .env is copied here)
            sh "docker build -t ts-api-engine-service-1606:${params.DOCKER_TAG} ."
            sh "docker push ts-api-engine-service-1606:${params.DOCKER_TAG} || echo 'Push failed - check credentials'"
        }
      }
    }

    stage('Deploy with Secrets') {
      steps {
        // Unlock secrets if encrypted
        // withCredentials([file(credentialsId: 'infra-repo-key', variable: 'KEY_FILE')]) {
        //   sh "git-crypt unlock ${KEY_FILE}"
        // }

        dir('ts-api-engine-service-1606') {
            echo "Deploying to ${params.BUILD_ENV} using runtime secret injection..."
            
            // 1. Get the absolute path to the secrets in infra repo
            def secretPath = "${WORKSPACE}/ts-infra-devops-5005/environments/${params.BUILD_ENV}/configs/ts-api-engine-service-1606/.env"
            
            // 2. Start the container, mounting the secrets from the build server
            // In a real production setup, you might use 'docker stack deploy' or sync these to the target server
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