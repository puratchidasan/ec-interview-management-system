pipeline {
    agent any

    /*
     * European External Action Service — Interview Management System
     * Jenkins Declarative Pipeline — DevSecOps Showcase
     *
     * Stages:
     *   1.  Checkout
     *   2.  Build
     *   3.  Unit Tests         (JUnit 5 + Mockito via Maven Surefire)
     *   4.  Integration Tests  (Spring Boot Test via Maven Failsafe)
     *   5.  Code Coverage      (JaCoCo — 70% line minimum)
     *   6.  SAST               (SonarCloud Quality Gate)
     *   7.  SCA                (OWASP Dependency-Check)
     *   8.  Docker Build       (multi-stage image)
     *   9.  Start App          (for DAST target)
     *  10.  DAST               (OWASP ZAP baseline scan)
     *  11.  Stop App
     *  12.  E2E Regression     (REST Assured full lifecycle)
     *  13.  Publish Reports
     *  14.  Archive Artifacts
     *  15.  Notify
     */

    environment {
        APP_NAME        = 'interview-management-system'
        APP_VERSION     = '1.0.0-SNAPSHOT'
        DOCKER_IMAGE    = "ec-ims/${APP_NAME}:${BUILD_NUMBER}"
        APP_PORT        = '8080'
        APP_URL         = "http://localhost:${APP_PORT}"
        JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'

        // Credentials stored in Jenkins credential store
        SONAR_TOKEN     = credentials('sonarcloud-token')
        DOCKER_CREDS    = credentials('docker-registry-creds')
    }

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-21'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        // ─────────────────────────────────────────────────────
        // Stage 1: Checkout
        // ─────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo '>>> Checking out source code...'
                checkout scm
                sh 'git log --oneline -5'
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 2: Build
        // ─────────────────────────────────────────────────────
        stage('Build') {
            steps {
                echo '>>> Building application with Maven (skip tests)...'
                sh 'mvn clean compile -DskipTests -B -q'
            }
            post {
                failure {
                    error 'Build failed — aborting pipeline.'
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 3: Unit Tests
        // ─────────────────────────────────────────────────────
        stage('Unit Tests') {
            steps {
                echo '>>> Running Unit Tests (Mockito + JUnit 5)...'
                sh '''
                    mvn test \
                        -Dspring.profiles.active=test \
                        -B -q
                '''
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/**/*.xml',
                          allowEmptyResults: false
                    echo "Unit Test Results: ${currentBuild.currentResult}"
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 4: Integration Tests
        // ─────────────────────────────────────────────────────
        stage('Integration Tests') {
            steps {
                echo '>>> Running Integration Tests (Spring Boot Test slices)...'
                sh '''
                    mvn failsafe:integration-test failsafe:verify \
                        -Dspring.profiles.active=test \
                        -Dsurefire.failIfNoSpecifiedTests=false \
                        -B -q
                '''
            }
            post {
                always {
                    junit testResults: 'target/failsafe-reports/**/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 5: Code Coverage (JaCoCo)
        // ─────────────────────────────────────────────────────
        stage('Code Coverage') {
            steps {
                echo '>>> Generating JaCoCo code coverage report...'
                sh 'mvn jacoco:report -B -q'
            }
            post {
                always {
                    jacoco(
                        execPattern:       'target/jacoco.exec',
                        classPattern:      'target/classes',
                        sourcePattern:     'src/main/java',
                        exclusionPattern:  '**/config/**,**/dto/**,**/entity/**,**/*Application*',
                        minimumLineCoverage: '70',
                        minimumBranchCoverage: '60'
                    )
                    publishHTML(target: [
                        reportDir:   'target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName:  'JaCoCo Coverage Report',
                        keepAll:     true
                    ])
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 6: SAST — SonarCloud
        // ─────────────────────────────────────────────────────
        stage('SAST — SonarCloud') {
            steps {
                echo '>>> Running SAST with SonarCloud...'
                withSonarQubeEnv('SonarCloud') {
                    sh '''
                        mvn sonar:sonar \
                            -Dsonar.login=${SONAR_TOKEN} \
                            -Dsonar.projectVersion=${APP_VERSION} \
                            -B -q
                    '''
                }
            }
            post {
                always {
                    timeout(time: 5, unit: 'MINUTES') {
                        script {
                            def qg = waitForQualityGate()
                            if (qg.status != 'OK') {
                                unstable("SonarCloud Quality Gate failed: ${qg.status}")
                            } else {
                                echo "SonarCloud Quality Gate: PASSED (${qg.status})"
                            }
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 7: SCA — OWASP Dependency-Check
        // ─────────────────────────────────────────────────────
        stage('SCA — OWASP Dependency-Check') {
            steps {
                echo '>>> Running Software Composition Analysis (OWASP Dependency-Check)...'
                sh '''
                    mvn org.owasp:dependency-check-maven:check \
                        -B -q \
                        || true
                '''
                // Note: "|| true" prevents build failure here; we assess via report
                // Remove "|| true" in strict mode to enforce CVSS >= 7 gate
            }
            post {
                always {
                    dependencyCheckPublisher(
                        pattern: 'target/dependency-check-report/dependency-check-report.xml',
                        failedTotalCritical: 0,
                        failedTotalHigh: 2,
                        unstableTotalMedium: 5
                    )
                    publishHTML(target: [
                        reportDir:   'target/dependency-check-report',
                        reportFiles: 'dependency-check-report.html',
                        reportName:  'OWASP Dependency-Check Report',
                        keepAll:     true
                    ])
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 8: Docker Build
        // ─────────────────────────────────────────────────────
        stage('Docker Build') {
            steps {
                echo ">>> Building Docker image: ${DOCKER_IMAGE}..."
                sh '''
                    mvn package -DskipTests -B -q
                    docker build -t ${DOCKER_IMAGE} .
                    docker tag ${DOCKER_IMAGE} ec-ims/${APP_NAME}:latest
                '''
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 9: Start Application (for DAST)
        // ─────────────────────────────────────────────────────
        stage('Start Application') {
            steps {
                echo '>>> Starting application container for DAST scan...'
                sh '''
                    docker rm -f ims-dast-target 2>/dev/null || true
                    docker run -d \
                        --name ims-dast-target \
                        -p ${APP_PORT}:8080 \
                        -e SPRING_PROFILES_ACTIVE=dev \
                        ${DOCKER_IMAGE}

                    echo "Waiting for application to become healthy..."
                    for i in $(seq 1 30); do
                        if curl -sf ${APP_URL}/actuator/health > /dev/null 2>&1; then
                            echo "Application is UP after ${i}s"
                            break
                        fi
                        sleep 2
                    done
                    curl -sf ${APP_URL}/actuator/health | grep -q '"status":"UP"'
                '''
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 10: DAST — OWASP ZAP
        // ─────────────────────────────────────────────────────
        stage('DAST — OWASP ZAP') {
            steps {
                echo '>>> Running DAST with OWASP ZAP baseline scan...'
                sh '''
                    mkdir -p zap/reports

                    docker run --rm \
                        --network host \
                        -v "$(pwd)/zap:/zap/wrk:rw" \
                        ghcr.io/zaproxy/zaproxy:stable \
                        zap-baseline.py \
                            -t ${APP_URL}/v3/api-docs \
                            -c /zap/wrk/zap-baseline.conf \
                            -r /zap/wrk/reports/zap-report.html \
                            -x /zap/wrk/reports/zap-report.xml \
                            -I \
                        || true
                '''
                // "-I" ignores failure — adjust to remove in strict mode
            }
            post {
                always {
                    publishHTML(target: [
                        reportDir:   'zap/reports',
                        reportFiles: 'zap-report.html',
                        reportName:  'OWASP ZAP DAST Report',
                        keepAll:     true
                    ])
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 11: Stop Application
        // ─────────────────────────────────────────────────────
        stage('Stop Application') {
            steps {
                echo '>>> Stopping DAST target container...'
                sh 'docker rm -f ims-dast-target 2>/dev/null || true'
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 12: E2E Regression Tests
        // ─────────────────────────────────────────────────────
        stage('E2E Regression Tests') {
            steps {
                echo '>>> Running End-to-End Regression Tests (REST Assured)...'
                sh '''
                    mvn failsafe:integration-test failsafe:verify \
                        -P regression \
                        -Dspring.profiles.active=test \
                        -B -q
                '''
            }
            post {
                always {
                    junit testResults: 'target/failsafe-reports/**/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 13: Publish Reports (summary)
        // ─────────────────────────────────────────────────────
        stage('Publish Reports') {
            steps {
                echo '>>> Publishing all security and test reports...'
                // Reports are already published in each stage's post block
                // This stage provides a final summary
                script {
                    echo """
                    ╔══════════════════════════════════════════════════════╗
                    ║         DevSecOps Pipeline Report Summary            ║
                    ╠══════════════════════════════════════════════════════╣
                    ║  Build:              ${currentBuild.currentResult}                   ║
                    ║  Unit Tests:         target/surefire-reports/        ║
                    ║  Integration Tests:  target/failsafe-reports/        ║
                    ║  JaCoCo Coverage:    target/site/jacoco/index.html   ║
                    ║  SonarCloud SAST:    https://sonarcloud.io           ║
                    ║  OWASP SCA:          target/dependency-check-report/ ║
                    ║  OWASP ZAP DAST:     zap/reports/zap-report.html    ║
                    ║  E2E Regression:     target/failsafe-reports/        ║
                    ╚══════════════════════════════════════════════════════╝
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────
        // Stage 14: Archive Artifacts
        // ─────────────────────────────────────────────────────
        stage('Archive Artifacts') {
            steps {
                echo '>>> Archiving build artifacts...'
                archiveArtifacts artifacts: [
                    'target/*.jar',
                    'target/site/jacoco/**',
                    'target/dependency-check-report/**',
                    'target/surefire-reports/**',
                    'target/failsafe-reports/**',
                    'zap/reports/**'
                ].join(','), allowEmptyArchive: true, fingerprint: true
            }
        }
    }

    // ─────────────────────────────────────────────────────
    // Post Actions
    // ─────────────────────────────────────────────────────
    post {
        always {
            echo "Pipeline finished with status: ${currentBuild.currentResult}"
            // Clean up any leftover containers
            sh 'docker rm -f ims-dast-target 2>/dev/null || true'
        }

        success {
            echo '✅ Pipeline PASSED — All security gates cleared!'
            // Add notification here (e.g., Slack, email)
            // slackSend(color: 'good', message: "✅ IMS Pipeline #${BUILD_NUMBER} PASSED")
        }

        failure {
            echo '❌ Pipeline FAILED — Check reports for details.'
            // slackSend(color: 'danger', message: "❌ IMS Pipeline #${BUILD_NUMBER} FAILED")
        }

        unstable {
            echo '⚠️ Pipeline UNSTABLE — Review security findings.'
            // slackSend(color: 'warning', message: "⚠️ IMS Pipeline #${BUILD_NUMBER} UNSTABLE")
        }

        cleanup {
            cleanWs()
        }
    }
}
