#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * CI-RT checkout kernel
 */

import de.linutronix.cirt.VarNotSetException;
import de.linutronix.cirt.NoGitTagsException;
import de.linutronix.cirt.helper;
import de.linutronix.cirt.inputcheck;

def extractGitTagsInfo(Map global, String repo, String branch) {
	def gitdescr = libraryResource('de/linutronix/cirt/compiletest/gitdescribe.sh');
	writeFile file:"gitdescribe.sh", text:gitdescr;
	gitdescr = null;

	/*
	 * start with #!/bin/bash to circumvent Jenkins default shell
	 * options "-xe". Otherwise stderr is poluted with confusing
	 * shell trace output and bedevil the user notification.
	 */
	def gitscript = "#!/bin/bash\n. gitdescribe.sh false >> gittags.properties"

	/*
	 * gitdescribe.sh returns:
	 * 0 on success
	 * 1 on error ('git describe HEAD' is empty)
	 * 2 on usage error
	 */
	def ret = sh(script: gitscript, returnStatus: true);

	switch(ret) {
	case 0:
		break;
	case 1:
		throw new NoGitTagsException("$repo $branch");
	case 2:
		error("Usage error in gitdescribe.sh");
	default:
		error("Unknown abort in gitdescribe.sh");
	}

	/* Create STASH_GITTAGS */
	stash(includes: 'gittags.properties',
	      name: global.STASH_GITTAGS);
}

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

					extractGitTagsInfo(global, gitrepo, gitcheckout);
				}
			}
		}
	} catch(Exception ex) {
		if (ex instanceof VarNotSetException || NoGitTagsException) {
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
