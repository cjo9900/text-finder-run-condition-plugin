/*
 * The MIT License
 *
 * Copyright (C) 2012 by Chris Johnson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkins_ci.plugins.text_finder_run_condition;

import hudson.Extension;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.Util;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.jenkins_ci.plugins.run_condition.common.AlwaysPrebuildRunCondition;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Run condition to search files for text strings.
 * Based off the TextFinderPublisher
 *
 * @author Chris Johnson
 */
@Extension
public class TextFinderCondition extends AlwaysPrebuildRunCondition implements Serializable {

    public final String fileSet;
    public final String regexp;
    /**
     * True to also scan the whole console output
     */
    public final boolean checkConsoleOutput;

    /**
     * Data bound constructor taking a condition and exclusive cause
     *
     * @param condition         Build condition to use
     * @param exclusiveCause    flag to indicate whether builds started by multiple causes are allowed.
     */
    @DataBoundConstructor
    public TextFinderCondition(String fileSet, String regexp, boolean checkConsoleOutput) {
        this.fileSet = Util.fixEmpty(fileSet.trim());
        this.regexp = regexp;
        this.checkConsoleOutput = checkConsoleOutput;

    }

    public TextFinderCondition() {
        this.fileSet = null;
        this.regexp = null;
        this.checkConsoleOutput = false;
    }

    /**
     * Performs the check of the condition and exclusiveCause.
     *
     * @return false if more than single cause for the build
     *         Otherwise the result of the condition runPerform @see BuildCauseCondition
     */
    @Override
    public boolean runPerform(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        return findText(build, listener.getLogger());
    }

    /**
     * Indicates an orderly abortion of the processing.
     */
    private static final class AbortException extends RuntimeException {
    }

    private boolean findText(AbstractBuild build, PrintStream logger) throws IOException, InterruptedException {
        boolean foundText = false;
        try {
            if (checkConsoleOutput) {
                logger.println("Checking console output");
                foundText |= checkFile(build.getLogFile(), compilePattern(logger), logger);
            } else {
                // printing this when checking console output will cause the plugin
                // to find this line, which would be pointless.
                // doing this only when fileSet!=null to avoid
                logger.println("Checking " + regexp);
            }

            // no need to search through files if matched in console log
            if (foundText == true) {
                return foundText;
            }

            final RemoteOutputStream ros = new RemoteOutputStream(logger);

            if (fileSet != null) {
                foundText |= build.getWorkspace().act(new FileCallable<Boolean>() {

                    public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
                        PrintStream logger = new PrintStream(ros);

                        // Collect list of files for searching
                        FileSet fs = new FileSet();
                        org.apache.tools.ant.Project p = new org.apache.tools.ant.Project();
                        fs.setProject(p);
                        fs.setDir(ws);
                        fs.setIncludes(fileSet);
                        DirectoryScanner ds = fs.getDirectoryScanner(p);

                        // Any files in the final set?
                        String[] files = ds.getIncludedFiles();
                        if (files.length == 0) {
                            logger.println("Text Finder run condition: File set '"
                                    + fileSet + "' is empty");
                            throw new AbortException();
                        }

                        Pattern pattern = compilePattern(logger);

                        boolean foundText = false;

                        for (String file : files) {
                            File f = new File(ws, file);

                            if (!f.exists()) {
                                logger.println("Text Finder run condition: Unable to"
                                        + " find file '" + f + "'");
                                continue;
                            }
                            if (!f.canRead()) {
                                logger.println("Text Finder run condition: Unable to"
                                        + " read from file '" + f + "'");
                                continue;
                            }

                            foundText |= checkFile(f, pattern, logger);
                            if (foundText == true) {
                                // no need to search through rest of files if matched
                                break;
                            }
                        }

                        return foundText;
                    }
                });
            }
        } catch (AbortException e) {
        }
        return foundText;
    }

    /**
     * Search the given regexp pattern in the file.
     *
     * return immediately as soon as the first hit is
     * found as there is no need to check any further
     */
    private boolean checkFile(File f, Pattern pattern, PrintStream logger) {

        boolean foundText = false;
        BufferedReader reader = null;
        try {
            // Assume default encoding and text files
            String line;
            reader = new BufferedReader(new FileReader(f));
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    logger.println(f + ":");
                    logger.println(line);
                    foundText = true;
                    break;
                }
            }
        } catch (IOException e) {
            logger.println("Jenkins Text Finder: Error reading"
                    + " file '" + f + "' -- ignoring");
        } finally {
            IOUtils.closeQuietly(reader);
        }
        return foundText;
    }

    private Pattern compilePattern(PrintStream logger) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regexp);
        } catch (PatternSyntaxException e) {
            logger.println("Jenkins Text Finder: Unable to compile"
                    + "regular expression '" + regexp + "'");
            throw new AbortException();
        }
        return pattern;
    }

    @Extension
    public static class TextFinderConditionDescriptor extends RunConditionDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.TextFinderCondition_DisplayName();
        }
    }
}
