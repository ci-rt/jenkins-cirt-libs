#!/usr/bin/env groovy
// SPDX-License-Identifier: MIT
// Copyright (c) 2017,2018 Linutronix GmbH
/*
 * CI-RT library build environment with test-description
 */

import de.linutronix.cirt.inputcheck;
import de.linutronix.lib4lib.safesplit;

@NonCPS
private getRegrtestList(String globalenv) {
	def regrenv = globalenv =~ /\s*REGRESSION_TEST_[A-Z_]*=.*/
	def enabled = false;
	def list = "";

	regrenv.each { regr ->
		def val = safesplit.split(regr, '=').last();
		if (val.toBoolean()) {
			enabled = true;
			list += "${regr}";
		}
	}

	return [ list, enabled ];
}

private String readCleanFile(String filename) {
	def content = readFile(filename);

	/* remove all lines beginning with # (comments only) */
	content = content.replaceAll(/(?m)^#.*\n/, "");

	/* remove all empty lines */
	content = content.replaceAll(/(?m)^\s*\n/, "");

	return content;
}

private String list2prop(String list, String var) {
	if (!fileExists(list)) {
		return "${var}=\n";
	}

	def content;
	try {
		content = readCleanFile(list);
	} catch(Exception ex) {
		println(ex);
		error("readCleanFile >${list}< failed");
	}

	content = content.replaceAll("#.*", "");
	content = content.replace("\n", " ");
	content = var + "=" + content.trim() + "\n";

	return new String(content);
}

private String handleLists(String globalenv) {
	def lists = list2prop('env/compile.list', 'CONFIGS');
	lists += list2prop('env/overlay.list', 'OVERLAYS');
	lists += list2prop('env/email.list', 'RECIPIENTS');

	return new String(globalenv + lists);
}

private String prepareGlobalEnv(String globalenv, String commit) {
	def branch = '';
	/* no need to set PUBLICREPO if found earlier */
	def m = globalenv =~ /\s*PUBLICREPO\s*=.*/
	if (m.count) {
		publicrepo = '';
	} else {
		publicrepo = 'PUBLICREPO=true';
	}
	m = null;

	/*
	 * determine commit to build:
	 * 1. commit is set by gui
	 * 2. commit is set by test
	 * 3. fallback to branch
	 *
	 * Therefore, remove COMMIT from environment and
	 * set to an explicit value later on.
	 */

	if (commit.trim()) {
		/* remove COMMIT from environment */
		globalenv = globalenv - ~/\s*COMMIT\s*=.*/
	} else {
		commit = globalenv =~ /\s*COMMIT\s*=.*/
		if (commit.count) {
			commit -= ~/\s*COMMIT\s*=/

			/* remove COMMIT from environment */
			globalenv = globalenv - ~/\s*COMMIT\s*=.*/
		} else {
			branch = globalenv =~ /\s*BRANCH\s*=.*/
			commit = branch[0] - ~/\s*BRANCH\s*=/
		}
	}

	globalenv += """
COMMIT=${commit}
GIT_CHECKOUT=${commit}
SCHEDULER_ID=${env.BUILD_NUMBER}
ENV_ID=${BUILD_NUMBER}
CI_RT_URL=${env.BUILD_URL}
${publicrepo}
"""

	/*
	 * Drop all references to branch and commit after use.
	 * Otherwise JIT and GC may not be finished with cleanup
	 * and throw an serialization error exception in readCleanFile().
	 */
	commit = null;
	branch = null;

	/* remove all empty lines and whitespaces around [=] */
	globalenv = globalenv.replaceAll(/(?m)^\s*\n/, "");
	globalenv = globalenv.replaceAll(/\s*=\s*/, "=");

	return handleLists(globalenv);
}

private String buildGlobalEnv(String commit) {
	/* findFiles() throws an exception, when directory is empty */
	def filelist = findFiles(glob: '**/*.properties');
	if (filelist) {
		error("found .properties files in testdescription. CIRTbuildenv failed.");
	}

	def globalenv = readCleanFile("env/global");
	globalenv = prepareGlobalEnv(globalenv, commit);

	writeFile(file:"environment.properties", text:globalenv);

	return new String(globalenv);
}

private buildArchCompileEnv(List configs)
{
	def compilefile;
	def compile;

	if (fileExists("compile/env/compile")) {
		compilefile = readCleanFile("compile/env/compile");
		compile = safesplit.split(compilefile, '\n').collectEntries { entry ->
			def pair = safesplit.split(entry, '=');
			[(pair.first()): pair.last()]
		}
	} else {
		compile = [:];
	}

	/* Build a List of archs by manipulating the config list */
	def archs = configs.collect { config ->
		/* remove the trailing part of config to get arch */
		config = config.replaceAll("/.*", "");
		config
	}

	archs.unique().each { arch ->
		def archcompile;

		if (fileExists("compile/env/${arch}")) {
			def archcompilefile = readCleanFile("compile/env/${arch}");
			archcompile = safesplit.split(archcompilefile, '\n')
			.collectEntries { entry ->
				def pair = safesplit.split(entry, '=')
				[(pair.first()): pair.last()]
			}
		} else {
			archcompile = [:];
		}

		archcompile['ARCH'] = arch;

		def map = compile.clone();
		def properties = "";
		archcompile.each { k, v -> map[k] = v }
		map.each { k, v ->
			if (k)
				properties += "${k.trim()}=${v.trim()}\n";
		}
		writeFile(file:"compile/env/${arch}.properties", text:properties);
	}
}

private prepareCyclictestEnv(String content) {
	def testsprop = "";

	if (content =~ /\s*CYCLICTEST\s*=.*/) {
		def cyclictest = content =~ /\s*CYCLICTEST\s*=.*/
		cyclictest = cyclictest[0] - ~/\s*CYCLICTEST\s*=/

		cyclictests = list2prop(cyclictest, "CYCLICTESTS");
		cyclictest = null

		testsprop = cyclictests;

		cyclictests -= ~/\s*CYCLICTESTS\s*=/
		cyclictests = safesplit.split(cyclictests);

		for (int i = 0; i < cyclictests.size(); i++) {
			def ct = cyclictests.getAt(i);
			sh("cp ${ct} ${ct}.properties");
		}
	}

	return testsprop;
}

private buildBootTestEnv(List boottests) {
	for (int i = 0; i < boottests.size(); i++) {
		def test = boottests.getAt(i);
		def content = readCleanFile(test);
		content += prepareCyclictestEnv(content);

		writeFile(file:"${test}.properties", text:content);
	}
}

private prepareCompBootEnv(String config, String overlay) {
	sh """\
#! /bin/bash

set -e

export config=${config}
export overlay=${overlay}
""" + '''\
ARCH=$(dirname $config)
CONFIGNAME=$(basename $config)

BOOTTESTS=" "

echo "building compile/env/${ARCH}_${CONFIGNAME}_${overlay}.properties"

# Read env/boottest.list line by line
while read line
do
	case $line in
		# ignore comments
		"#"*)
			;&
		"")
			continue
			;;
		# find $config and $overlay in all other boottest files
		*)
			if grep -q -G "^CONFIG[ ]*=[ ]*${config}$" $line && grep -q -G "^OVERLAY[ ]*=[ ]*${overlay}$" $line
			then
				BOOTTESTS="$BOOTTESTS $line"
			fi
			;;
	esac
done < env/boottest.list

# create compile/env/$ARCH_$NAME_$OVERLAY.properties file
if [ ! -z $BOOTTESTS ]
then
	echo "BOOTTESTS=$BOOTTESTS" > compile/env/${ARCH}_${CONFIGNAME}_${overlay}.properties
else
	echo "No boottest configured: Property file creation skipped."
fi
'''
}

private buildCompBootEnv(List configs, List overlays) {
	configs.each { config ->
		overlays.each { overlay ->
			prepareCompBootEnv(config, overlay);
		}
	}
}

private String getValue(String content, String key) {
	def value = content =~ /\s*${key}\s*=.*/
	if (value.count) {
		value = value[0] - ~/\s*${key}\s*=/
	} else {
		value = " ";
	}
	return new String(value);
}

private buildCompileEnv(String globalenv) {
	List configs = safesplit.split(getValue(globalenv, "CONFIGS"));
	List overlays = safesplit.split(getValue(globalenv, "OVERLAYS"));
	def bootlist = list2prop('env/boottest.list', 'BOOTTESTS_ALL');
	List boottests = safesplit.split(getValue(bootlist, "BOOTTESTS_ALL"));

	buildArchCompileEnv(configs);

	if (boottests) {
		buildCompBootEnv(configs, overlays);
		buildBootTestEnv(boottests);
	}
}

private buildEnv(String commit) {
	String globalenv = buildGlobalEnv(commit);
	buildCompileEnv(globalenv);

	return globalenv;
}

private notifyRegrtest(String recipients, String globalenv) {
	def enabled = false;
	def list = "";

	(list, enabled) = getRegrtestList(globalenv)

        if (!enabled)
                return;

	notify("${recipients}",
	       "Regression test enabled!",
	       "regressiontest",
	       null, false,
	       ["regressionenv": list]);
}

def call(String commit, Map global) {
	inputcheck.check(global);
	try {
		node('master') {
			def globalenv;
			def recipients;
			dir('environment') {
				deleteDir();
				unstash(global.STASH_RAWENV);

				globalenv = buildEnv(commit);

				recipients = getValue(globalenv, "RECIPIENTS");

				notifyRegrtest(recipients, globalenv);

				/*
				 * Stash *.properties files; directory
				 * hierarchy doesn't change
				 */
				stash(includes: '**/*.properties',
				      name: global.STASH_PRODENV);
				stash(includes: 'patches/**',
				      name: global.STASH_PATCHES,
				      allowEmpty: true);
				stash(includes: 'compile/configs/**, compile/overlays/**',
				      name: global.STASH_COMPILECONF);
			}
			archiveArtifacts(artifacts: 'environment/**/*.properties, environment/patches/**, environment/compile/configs/**, environment/compile/overlays/**',
					 fingerprint: true);

			return recipients;
		}
	} catch(Exception ex) {
		println("CIRTbuildenv failed:");
		println(ex.toString());
		println(ex.getMessage());
		println(ex.getStackTrace());
		error("CIRTbuildenv failed.");
	}
}

def call(String... params) {
	println params
	error("Unknown signature. Abort.");
}
