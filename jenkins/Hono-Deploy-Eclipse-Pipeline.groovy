#!/usr/bin/env groovy

/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

/**
 * Jenkins pipeline script that checks out ${RELEASE_VERSION}, builds all artifacts to deploy,
 * signs them and creates PGP signatures for them and deploys artifacts to Eclipse Release repo.
 *
 */

node {
    def utils = evaluate readTrusted("jenkins/Hono-PipelineUtils.groovy")
    properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3')), parameters([
            string(
                name: 'BRANCH',
                description: "The branch to read the pipeline definition from.\nExamples:\n refs/heads/master\nrefs/heads/1.3.x",
                defaultValue: '',
                trim: true),
            string(
                name: 'RELEASE_VERSION',
                description: "The tag to build and deploy.\nExamples:\n1.0.0-M6\n1.0.0-RC1\n2.1.0",
                defaultValue: '',
                trim: true)
    ])])
    try {
        utils.checkOutRepoWithCredentials("refs/tags/${params.RELEASE_VERSION}", "github-bot-ssh", "ssh://git@github.com/eclipse/hono.git")
        buildAndDeploy(utils)
        currentBuild.result = 'SUCCESS'
    } catch (err) {
        currentBuild.result = 'FAILURE'
        echo "Error: ${err}"
    }
    finally {
        echo "Build status: ${currentBuild.result}"
        utils.notifyBuildStatus()
    }
}

/**
 * Build and deploy with maven.
 *
 * @param utils An instance of the Hono-PipelineUtils containing utility methods to build pipelines.
 */
def buildAndDeploy(def utils) {
    stage('Build and deploy to Eclipse Repo') {
        withMaven(
          maven: utils.getMavenVersion(),
          jdk: utils.getJDKVersion(),
          options: [artifactsPublisher(disabled: true)]) {
            sh "mvn --projects :hono-service-auth,:hono-service-device-registry-file,:hono-service-device-registry-jdbc,:hono-service-device-registry-mongodb,:hono-service-command-router,:hono-service-device-connection,:hono-adapter-http-vertx,:hono-adapter-mqtt-vertx,:hono-adapter-kura,:hono-adapter-amqp-vertx,:hono-adapter-lora-vertx,:hono-adapter-sigfox-vertx,:hono-adapter-coap-vertx,:hono-example,:hono-cli -am deploy -DskipTests=true -DcreateJavadoc=true -DenableEclipseJarSigner=true -DskipStaging=true"
        }
    }
}
