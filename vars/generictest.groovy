#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT generictest
 */

import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.inputcheck;
import de.linutronix.cirt.helper;

private failnotify(Map global, helper h, String target,
		   String generictestdir, String recipients)
{
	def testscript = h.getVar("SCRIPT");
	def repo = h.getVar("GITREPO");
	def branch = h.getVar("BRANCH");
	def config = h.getVar("CONFIG");
	def overlay = h.getVar("OVERLAY");
	def results = "results/${config}/${overlay}";

	dir("failurenotification") {
		deleteDir();
		unstash(results.replaceAll('/','_'));

		def gittags = readFile "${results}/compile/gittags.properties";
		gittags = gittags.replaceAll(/(?m)^\s*\n/, "");

		notify("${recipients}",
		       "generictest-runner failed!",
		       "generictestRunner",
		       "histogram.*",
		       false,
		       ["global": global, "repo": repo,
			"branch": branch, "config": config,
			"overlay": overlay, "target": target,
			"testscript": testscript,
			"gittags": gittags]);
	}
}

def call(Map global, String target, String[] generictests, String recipients) {
	try {
		inputcheck.check(global);
		node('master') {
			dir ("generictest") {
				deleteDir()
				for (int i = 0; i < generictests.size(); i++) {
					def gt = generictests[i];

					/*
					 * Generictest runner is executed on target;
					 * workspace directory doesn't has to be changed
					 */
					generictestRunner(global, target, gt);
				}
			}
		}
	} catch(VarNotSetException ex) {
		/*
		 * Notify here to avoid nested exception handling in
		 * boottestRunner.
		 */
		notify("${recipients}",
		       "Testdescription is not valid",
		       "CIRTexception",
		       null,
		       false,
		       ["failureText": ex.toString()]);
		currentBuild.result = 'UNSTABLE';
	} catch(Exception ex) {
		println("generictest on ${target} failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("generictest on ${target} failed.");
	}
}

def call(String... params) {
	println(params);
	error("Unknown signature. Abort.");
}
