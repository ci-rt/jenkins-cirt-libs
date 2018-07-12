#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2018 Linutronix GmbH
/*
 * git checkout helper
 *
 * The stash name is build by concat repo and branch, replacing colon and
 * slashes by underscore:
 *   repo:   git://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git
 *   branch: master
 *   stash:  git___git.kernel.org_pub_scm_linux_kernel_git_torvalds_linux.git_master
 *
 * Caution!
 * Jenkins stashes handle symbolic links as copies.
 * If links are essential (like in Linux Kernel Source) the following
 * workaround can be used to restore the symbolic links (and all other
 * special file nodes likewise):
 *
 *	unstash("name_of_git_stash");
 *	sh("git clean -dxf");
 *	sh("git reset --hard");
 *
 */

def call (String repo, String branch, String reference) {
	ArrayList extensions = [];

	Map gitopts = [$class: 'GitSCM',
		       branches: [[name: "${branch}"]],
		       doGenerateSubmoduleConfigurations: false,
		       userRemoteConfigs: [[url: "${repo}"]],
		       submoduleCfg: []];

	Map cloneopts = [$class: 'CloneOption',
			 depth: 1, noTags: false,
			 shallow: true, timeout: 60];

	if (reference) {
		cloneopts['reference'] = reference;
	}

	extensions << cloneopts;

	gitopts['extensions'] = extensions;
	checkout(gitopts);

	def srcstash = "${repo}_${branch}".replaceAll(/[\/:]/,'_');
	stash(name: srcstash, useDefaultExcludes: false);
}

def call(String... params) {
	println params;
	error("Unknown signature. Abort.");
}
