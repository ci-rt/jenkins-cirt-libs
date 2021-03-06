#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT scheduler job
 */

import de.linutronix.cirt.NoGitTagsException;
import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.inputcheck;

def call(body) {
	def recipients;
	// evaluate the body block, and collect configuration into the object
	def presets = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = presets
	body()

	/*
	 * Prevent executing tests and feeding database, when there was an known
	 * error in stage 'checkout kernel source'. On unknown errors pipeline
	 * is aborted anyway.
	 */
	def checkoutError = false;

	pipeline {
		agent any;

		options {
			timestamps()
		}

		parameters {
			string(defaultValue: "${presets.STASH_PATCHES ?: 'patches'}", description: '', name: 'STASH_PATCHES')
			string(defaultValue: "${presets.STASH_PRODENV ?: 'prodenv'}", description: '', name: 'STASH_PRODENV')
			string(defaultValue: "${presets.STASH_RAWENV ?: 'rawenvironment'}", description: '', name: 'STASH_RAWENV')
			string(defaultValue: "${presets.STASH_COMPILECONF ?: 'compileconf'}", description: '', name: 'STASH_COMPILECONF')
			string(defaultValue: "${presets.STASH_GITTAGS ?: 'gittagsstash'}", description: '', name: 'STASH_GITTAGS')
			string(defaultValue: "${presets.GUI_DB_HOSTNAME ?: 'localhost:5432'}", description: 'Hostname of database', name: 'GUI_DB_HOSTNAME')
			string(defaultValue: "${presets.GUI_COMMIT ?: ''}", description: '', name: 'GUI_COMMIT')
			string(defaultValue: "${presets.GUI_FAILURE_NOTIFICATION}", description: 'Notify in case of test system failure', name: 'GUI_FAILURE_NOTIFICATION')
		}

		stages {
			stage('inputcheck') {
				steps {
					CIRTinputcheck(params)
					echo "global variables set."
				}
			}

			stage('stash test-description') {
				steps {
					/*
					 * Cleanup Workspace before stash
					 * (except checked out git repo)
					 */
					sh "git clean -dfx";
					stash("${params.STASH_RAWENV}");
				}
			}

			stage('build CI-RT environment') {
				steps {
					script {
						try {
							recipients = CIRTbuildenv(params.GUI_COMMIT, params);
						} catch(Exception ex) {
							println("build environment failed:");
							println(ex.toString());
							println(ex.getMessage());
							println(ex.getStackTrace());
							error("build environment failed.");
						}
					}
				}
			}

			stage('notify test start') {
				steps {
					notify("${recipients}",
					       "Start",
					       "start",
					       false);
				}
			}

			stage('checkout kernel source') {
				steps {
					script {
						try {
							checkoutKernel(params);
						} catch(VarNotSetException ex) {
							notify("${recipients}",
							       "Testdescription is not valid",
							       "CIRTexception",
							       null,
							       false,
							       ["failureText": ex.toString()]);
							currentBuild.result = 'UNSTABLE';
							checkoutError = true;
						} catch(NoGitTagsException ex) {
							notify("${recipients}",
							       "Missing git tags",
							       "CIRTexception",
							       null,
							       false,
							       ["failureText": ex.toString()]);
							currentBuild.result = 'UNSTABLE';
							checkoutError = true;
						} catch(Exception ex) {
							println("kernel checkout failed:");
							println(ex.toString());
							println(ex.getMessage());
							println(ex.getStackTrace());
							error("kernel checkout failed.");
						}
					}
				}
			}

			stage('compile') {
				steps {
					script {
						if (checkoutError) {
							/* Abort this stage */
							return;
						}
						try {
							compiletest(params);
						} catch(VarNotSetException ex) {
							notify("${recipients}",
							       "Testdescription is not valid",
							       "CIRTexception",
							       null,
							       false,
							       ["failureText": ex.toString()]);
							currentBuild.result = 'UNSTABLE';
						} catch(Exception ex) {
							println("compiletest failed:");
							println(ex.toString());
							println(ex.getMessage());
							println(ex.getStackTrace());
							error("compiletest failed.");
						}
					}
				}
			}

			stage('collect and inform') {
				environment {
					DB_HOSTNAME = "${params.GUI_DB_HOSTNAME}";
				}
				steps {
					echo "feed database"
					withCredentials([[$class: 'UsernamePasswordMultiBinding',
							  credentialsId: 'POSTGRES_CREDENTIALS',
							  usernameVariable: 'DB_USER',
							  passwordVariable: 'DB_PASS']]) {
						script {
							if (checkoutError) {
								/* Abort this stage */
								return;
							}
							try {
								feeddatabase(params);
							} catch(VarNotSetException ex) {
								notify("${recipients}",
								       "Testdescription is not valid",
								       "CIRTexception",
								       null,
								       false,
								       ["failureText": ex.toString()]);
								currentBuild.result = 'UNSTABLE';
							} catch(Exception ex) {
								println("feeddatabase failed:");
								println(ex.toString());
								println(ex.getMessage());
								println(ex.getStackTrace());
								error("feeddatabase failed.");
							}
						}
					}
				}
			}
		}

		post {
			failure {
				script {
					try {
						if (params.GUI_FAILURE_NOTIFICATION) {
							notify("${params.GUI_FAILURE_NOTIFICATION}",
							       "internal failure");
						}
					} catch(Exception ex) {
						println("notification failed:");
						println(ex.toString());
						println(ex.getMessage());
						println(ex.getStackTrace());
					}
				}
			}

			always {
				script {
					try {
						warnings(canResolveRelativePaths: false,
							 canRunOnFailed: true,
							 categoriesPattern: '',
							 defaultEncoding: '',
							 excludePattern: '',
							 healthy: '',
							 includePattern: '',
							 messagesPattern: '',
							 parserConfigurations: [[parserName: 'GNU Make + GNU C Compiler (gcc)', pattern: '**/compile.log'],
										[parserName: 'Linux Kernel Makefile Errors', pattern: '**/compile.log'],
										[parserName: 'Linux Kernel Output Parser', pattern: '**/boot.log']],
							 unHealthy: '',
							 useStableBuildAsReference: true);

						notify("${recipients}",
						       "${currentBuild.currentResult}",
						       "cirt-scheduler",
						       ["status": currentBuild.currentResult]);
					} catch(Exception ex) {
						println("notification failed:");
						println(ex.toString());
						println(ex.getMessage());
						println(ex.getStackTrace());
					}
				}
			}
		}
	}
}
