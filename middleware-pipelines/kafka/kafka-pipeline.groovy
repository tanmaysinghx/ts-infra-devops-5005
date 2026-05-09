pipeline {

    agent any

    environment {

        NETWORK_NAME = 'kafka-network'

        KAFKA_CONTAINER = 'kafka-server'

        ZOOKEEPER_CONTAINER = 'zookeeper-server'

        KAFKA_UI_CONTAINER = 'kafka-ui'

        KAFKA_VOLUME = 'kafka-data'

        ZOOKEEPER_VOLUME = 'zookeeper-data'
    }

    stages {

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

                sh """
                    docker volume inspect ${ZOOKEEPER_VOLUME} >/dev/null 2>&1 || \
                    docker volume create ${ZOOKEEPER_VOLUME}
                """
            }
        }

        stage('Stop Existing Containers') {

            steps {

                sh "docker rm -f ${KAFKA_UI_CONTAINER} || true"

                sh "docker rm -f ${KAFKA_CONTAINER} || true"

                sh "docker rm -f ${ZOOKEEPER_CONTAINER} || true"
            }
        }

        stage('Deploy Zookeeper') {

            steps {

                sh """
                    docker run -d \
                      --name ${ZOOKEEPER_CONTAINER} \
                      --network ${NETWORK_NAME} \
                      -p 2181:2181 \
                      -e ALLOW_ANONYMOUS_LOGIN=yes \
                      -v ${ZOOKEEPER_VOLUME}:/bitnami/zookeeper \
                      --restart unless-stopped \
                      bitnami/zookeeper:latest
                """
            }
        }

        stage('Deploy Kafka') {

            steps {

                sh """
                    docker run -d \
                      --name ${KAFKA_CONTAINER} \
                      --network ${NETWORK_NAME} \
                      -p 9092:9092 \
                      -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper-server:2181 \
                      -e ALLOW_PLAINTEXT_LISTENER=yes \
                      -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092 \
                      -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://57.128.171.127:9092 \
                      -v ${KAFKA_VOLUME}:/bitnami/kafka \
                      --restart unless-stopped \
                      bitnami/kafka:latest
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

        stage('Verify Deployment') {

            steps {

                sh "docker ps"
            }
        }
    }
}