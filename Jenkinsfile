pipeline {
    agent any

    tools {
        maven 'Maven 3.8'
        jdk 'Java 21'
    }

    environment {
        DOCKER_REGISTRY_URL = 'registry:5000'

        IMAGE_NAME = 'user/spring-boot-app'
        IMAGE_TAG = "${env.BUILD_ID}"
        FULL_IMAGE_PATH = "${DOCKER_REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}"

        DOCKER_HOST = "unix:///var/run/docker.sock"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Unit Tests') {
            steps {
                sh 'mvn clean test -Dexcludes="**/*IT.java,**/*SystemTest.java"'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration & System Tests') {
            steps {
                sh 'mvn test -Dtest="*IT,*SystemTest"'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    dockerImage = docker.build("${FULL_IMAGE_PATH}")
                }
            }
        }

        stage('Deploy to Docker Registry') {
            steps {
                script {
                    docker.withRegistry("http://${DOCKER_REGISTRY_URL}") {
                        dockerImage.push()
                        dockerImage.push('latest')
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Success! Image ${FULL_IMAGE_PATH} is available in Docker Registry."
        }
        failure {
            echo "Failure! Check logs."
        }
    }
}
