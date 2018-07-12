#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT checkout kernel
 */

import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.helper;
import de.linutronix.cirt.inputcheck;

def call(Map global) {
	try {
		node ('kernel') {
			dir('srcrepo') {
				deleteDir();
				unstash(global.STASH_PRODENV);
				String[] properties = ["environment.properties"];
				def h = new helper();
				def gitrepo = "";
				def gitcheckout = "";

				h.add2environment(properties);

				gitrepo = h.getVar('GITREPO');
				gitcheckout = h.getVar('GIT_CHECKOUT');

				h = null;
				dir('src') {
					def ref = env.LINUX_KERNEL_MIRROR;
					gitCheckout(gitrepo, gitcheckout, ref);
				}
			}
		}
	} catch(Exception ex) {
		if (ex instanceof VarNotSetException) {
			throw ex;
		}

		println("kernel checkout failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("kernel checkout failed.");
	}
}

def call(String... params) {
	println params;
	error("Unknown signature. Abort.");
}
