pipeline {

    agent any

    environment {

        NETWORK_NAME = 'kafka-network'

        KAFKA_CONTAINER = 'kafka-server'

        KAFKA_UI_CONTAINER = 'kafka-ui'

        KAFKA_VOLUME = 'kafka-data'

        VPS_IP = '57.128.171.127'
    }

    stages {

        stage('Checkout Source') {

            steps {
                checkout scm
            }
        }

        stage('Prepare Network and Volumes') {

            steps {

                sh """
                    docker network inspect ${NETWORK_NAME} >/dev/null 2>&1 || \
                    docker network create ${NETWORK_NAME}
                """

                sh """
                    docker volume inspect ${KAFKA_VOLUME} >/dev/null 2>&1 || \
                    docker volume create ${KAFKA_VOLUME}
                """
            }
        }

        stage('Stop Existing Containers') {

            steps {

                sh "docker rm -f ${KAFKA_UI_CONTAINER} || true"

                sh "docker rm -f ${KAFKA_CONTAINER} || true"
            }
        }

        stage('Pull Images') {

            steps {

                sh 'docker pull apache/kafka:latest'

                sh 'docker pull provectuslabs/kafka-ui:latest'
            }
        }

        stage('Deploy Kafka KRaft') {

            steps {

                sh """
                    docker run -d \
                      --name ${KAFKA_CONTAINER} \
                      --network ${NETWORK_NAME} \
                      -p 9092:9092 \
                      -e KAFKA_NODE_ID=1 \
                      -e KAFKA_PROCESS_ROLES=broker,controller \
                      -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
                      -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${VPS_IP}:9092 \
                      -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
                      -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
                      -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
                      -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
                      -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
                      -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
                      -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
                      -e KAFKA_NUM_PARTITIONS=3 \
                      -v ${KAFKA_VOLUME}:/var/lib/kafka/data \
                      --restart unless-stopped \
                      apache/kafka:latest
                """
            }
        }

        stage('Deploy Kafka UI') {

            steps {

                sh """
                    docker run -d \
                      --name ${KAFKA_UI_CONTAINER} \
                      --network ${NETWORK_NAME} \
                      -p 8085:8080 \
                      -e KAFKA_CLUSTERS_0_NAME=production \
                      -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka-server:9092 \
                      --restart unless-stopped \
                      provectuslabs/kafka-ui:latest
                """
            }
        }

        stage('Wait For Kafka Startup') {

            steps {

                echo 'Waiting for Kafka startup...'

                sh 'sleep 40'
            }
        }

        stage('Verify Deployment') {

            steps {

                sh 'docker ps'

                sh "docker logs ${KAFKA_CONTAINER} --tail 50"

                sh "docker logs ${KAFKA_UI_CONTAINER} --tail 50"
            }
        }
    }

    post {

        success {

            echo 'Kafka infrastructure deployed successfully.'

            echo 'Kafka Broker Port: 9092'

            echo 'Kafka UI Port: 8085'
        }

        failure {

            echo 'Kafka deployment failed.'
        }

        always {

            sh "docker ps || true"
        }
    }
}