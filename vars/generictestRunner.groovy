#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT generictest runner
 */

import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.helper;

private runner(Map global, String target, String generictest) {
	unstash(global.STASH_PRODENV);

	def h = new helper();
	String[] properties = ["environment.properties",
			       "boot/${target}.properties",
			       "${generictest}.properties"];

	h.add2environment(properties);
	properties = null;

	def script = h.getVar("SCRIPT", " ").trim();
	def config = h.getVar("CONFIG");
	def overlay = h.getVar("OVERLAY");
	h = null;

	def kernel = "${config}/${overlay}";
	def generictestdir = "results/${kernel}/${target}/${generictest}";
	def props = readFile("${generictest}.properties");

	dir(generictestdir) {
		deleteDir();
		unstash("generictest_boot_${target}".replaceAll('/','_'));
		println("generictest-runner: ${target} ${generictest} ${script}");
		def content = readFile(script);

		/* determine shebang */
		def shebang = "#! /bin/bash"
		if (content.substring(0, 2) == "#!") {
			shebang = content.split('\n')[0];
			content = content.split('\n').drop(1).join('\n');
		}

		content = shebang + "\n" + props + "\n" + content;
		writeFile(file:"generictest.sh", text:content);
		content = null;
		props = null;

		shunit("generictest", "${generictest}", "bash generictest.sh");
	}

	def result = junit_result("${generictestdir}/pyjutest.xml");
	archiveArtifacts("${generictestdir}/pyjutest.xml, ${generictestdir}/generictest.sh");
}

def call(Map global, String target, String generictest) {
	node(target) {
		try {
			dir("generictestRunner") {
				deleteDir();
				runner(global, target, generictest);
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
