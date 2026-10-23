pipeline {
    agent any

    environment {

        CONTAINER_NAME = 'oracle-db'

        IMAGE_NAME = 'container-registry.oracle.com/database/free:latest'

        NETWORK_NAME = 'backend-database-net'

        ORACLE_VOLUME = 'oracle-db-volume'

        ORACLE_PORT = '1521'

        // Jenkins "Secret text" credential holding the SYS/SYSTEM/PDBADMIN password
        ORACLE_PWD_CRED_ID = 'oracle-db-password'
    }

    stages {

        stage('Checkout Source') {
            steps {
                checkout scm
            }
        }

        stage('Prepare Docker Network and Volume') {
            steps {
                script {

                    sh """
                        docker network inspect ${NETWORK_NAME} >/dev/null 2>&1 || \
                        docker network create ${NETWORK_NAME}
                    """

                    sh """
                        docker volume inspect ${ORACLE_VOLUME} >/dev/null 2>&1 || \
                        docker volume create ${ORACLE_VOLUME}
                    """
                }
            }
        }

        stage('Stop Existing Oracle Container') {
            steps {
                script {
                    sh "docker stop ${CONTAINER_NAME} || true"
                    sh "docker rm ${CONTAINER_NAME} || true"
                }
            }
        }

        stage('Pull Latest Oracle Image') {
            steps {
                sh "docker pull ${IMAGE_NAME}"
            }
        }

        stage('Deploy Oracle Database') {
            steps {

                withCredentials([string(credentialsId: env.ORACLE_PWD_CRED_ID, variable: 'ORACLE_PWD')]) {
                    // Single-quoted so the secret is expanded by the shell, never interpolated by Groovy
                    sh '''
                        docker run -d \
                          --name "$CONTAINER_NAME" \
                          --network "$NETWORK_NAME" \
                          -p "$ORACLE_PORT":1521 \
                          -p 5500:5500 \
                          -e ORACLE_PWD \
                          -e ORACLE_CHARACTERSET=AL32UTF8 \
                          -v "$ORACLE_VOLUME":/opt/oracle/oradata \
                          --restart unless-stopped \
                          "$IMAGE_NAME"
                    '''
                }
            }
        }

        stage('Wait for Oracle Startup') {
            steps {

                echo "Waiting for Oracle DB startup..."

                sh "sleep 120"
            }
        }

        stage('Verify Oracle Container') {
            steps {

                sh "docker ps -f name=${CONTAINER_NAME}"

                sh "docker logs ${CONTAINER_NAME} --tail 50"
            }
        }
    }

    post {

        success {

            echo 'Oracle Database deployed successfully with persistent volume.'
        }

        failure {

            echo 'Oracle deployment failed.'
        }

        always {

            sh "docker logs ${CONTAINER_NAME} --tail 100 || true"
        }
    }
}
