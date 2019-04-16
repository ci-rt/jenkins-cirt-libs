#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT generictest runner
 */

import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.helper;

import groovy.util.XmlSlurper;

@NonCPS
private String prepareScript(String props, String content) {
	/* determine shebang */
	def shebang = "#! /bin/bash"
	if (content.substring(0, 2) == "#!") {
		shebang = content.split('\n')[0];
		content = content.split('\n').drop(1).join('\n');
	}

	def vars = '';
	props.eachLine {
		if (it =~ /GENERIC_/) {
			vars = vars + it + '\n';
		}
	}

	return shebang + "\n" + vars + "\n" + content;
}

@NonCPS
private String prepareResultData(String resultXml) {
	def list = new XmlSlurper().parseText(resultXml);

	def systemOut = list?.testsuite?.testcase?.'system-out';
	def systemErr = list?.testsuite?.testcase?.'system-err';

	return "STDOUT:\n" + systemOut + "\nSTDERR:\n" + systemErr;
}

private failnotify(Map global, String target, String generictestdir,
		   String repo, String branch, String config, String overlay,
		   String recipients)
{
	def resultXml = readFile("${generictestdir}/pyjutest.xml");

	dir("failurenotification") {
		deleteDir();

		/*
                 * Specifying a relative path starting with "../" does not
                 * work in notify attachments.
                 * Copy generictest results into this folder.
                 */
		sh("cp ../${generictestdir}/generictest.* .");

		def log = prepareResultData(resultXml);
		writeFile(file:"generictest.log", text:log);
		log = null;

		notify("${recipients}",
		       "generictest-runner failed!",
		       "generictestRunner",
		       "generictest.*",
		       false,
		       ["global": global, "repo": repo,
			"branch": branch, "config": config,
			"overlay": overlay, "target": target]);
	}
}

private runner(Map global, String target, String generictest,
	       String recipients) {
	unstash(global.STASH_PRODENV);

	def h = new helper();
	String[] properties = ["environment.properties",
			       "boot/${target}.properties",
			       "${generictest}.properties"];

	h.add2environment(properties);
	properties = null;

	def script = h.getVar("SCRIPT", " ").trim();
	def branch = h.getVar("BRANCH");
	def config = h.getVar("CONFIG");
	def overlay = h.getVar("OVERLAY");
	def repo = h.getVar("GITREPO");
	h = null;

	def kernel = "${config}/${overlay}";
	def generictestdir = "results/${kernel}/${target}/${generictest}";
	def props = readFile("${generictest}.properties");

	dir(generictestdir) {
		deleteDir();
		unstash("generictest_boot_${target}".replaceAll('/','_'));
		println("generictest-runner: ${target} ${generictest} ${script}");
		def content = readFile(script);
		content = prepareScript(props, content);
		writeFile(file:"generictest.sh", text:content);
		content = null;
		props = null;

		shunit("generictest", "${generictest}", "bash generictest.sh");
	}

	def result = junit_result("${generictestdir}/pyjutest.xml");
	archiveArtifacts("${generictestdir}/pyjutest.xml, ${generictestdir}/generictest.*");

	stash(name: generictestdir.replaceAll('/','_'),
	      includes: "${generictestdir}/*");

	if (result == 'UNSTABLE') {
		failnotify(global, target, generictestdir, repo, branch,
			   config, overlay, recipients);
	}
}

def call(Map global, String target, String generictest, String recipients) {
	node(target) {
		try {
			dir("generictestRunner") {
				deleteDir();
				runner(global, target, generictest, recipients);
			}
		} catch(Exception ex) {
			if (ex instanceof VarNotSetException) {
				throw ex;
			}
			println("generictest runner on ${target} failed:");
			println(ex.toString());
			println(ex.getMessage());
			println(ex.getStackTrace());
			error("generictest runner on ${target} failed.");
		}
	}
}

def call(String... params) {
	println(params);
	error("Unknown signature. Abort.");
}
