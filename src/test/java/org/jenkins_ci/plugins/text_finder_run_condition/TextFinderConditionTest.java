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

import hudson.model.*;
import hudson.FilePath;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

public class TextFinderConditionTest extends HudsonTestCase {
    //-------------------------------------------------------

    @Test
    public void testFileMatching() throws Exception {

        TextFinderCondition condition = new TextFinderCondition("*.txt", "No match", false);
        runtest(condition, false);

        condition = new TextFinderCondition("*.txt", "ANY match", false);
        runtest(condition, true);

        condition = new TextFinderCondition("*.txt", "T??t", false);
        runtest(condition, true);
    }

    @Test
    public void testConsoleMatching() throws Exception {

        TextFinderCondition condition = new TextFinderCondition("", "No match", true);
        runtest(condition, false);

        condition = new TextFinderCondition("", "console match only", true);
        runtest(condition, true);

        condition = new TextFinderCondition("", "t??t", true);
        runtest(condition, true);

    }

    @Test
    public void testBothMatching() throws Exception {

        TextFinderCondition condition = new TextFinderCondition("*.txt", "No match", true);
        runtest(condition, false);

        condition = new TextFinderCondition("*.txt", "Some", true);
        runtest(condition, true);

        condition = new TextFinderCondition("*.txt", "T??t", false);
        runtest(condition, true);

    }

    private void runtest(TextFinderCondition condition, boolean expected) throws Exception {

        // extend and replace with passed in param if we are need to test more varing case.
        String consoleText = "Some text for the console\nconsole match only";
        String fileText = "Some Text to add to the file\nANY match";
        String fileExt = ".txt";
        int numberOfOtherFiles = 3;

        FreeStyleProject project = createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();

        FilePath ws = build.getWorkspace();

        if (ws == null) {
            System.out.println("build workspace not avalible in test");
        } else {
            //create file with matche in workspace
            FilePath newfile = ws.createTextTempFile("txtfinder_match", fileExt, fileText);
            //System.out.println("created: " + newfile.toString());

            while (numberOfOtherFiles > 0) {
                ws.createTextTempFile("txtfinder", fileExt, "");
                numberOfOtherFiles--;
            }

        }
        System.out.println("build logfile: " + build.getLogFile().toString());
        if (build.getLogFile().canWrite()) {
            OutputStream outputStream = new FileOutputStream(build.getLogFile());
            Writer writer = new OutputStreamWriter(outputStream);

            writer.write(consoleText);
            writer.close();

        } else {
            System.out.println("cannot write to logfile: " + build.getLogFile().toString());
        }

        //need to use a different listener if we are checking console log
        StreamBuildListener listener = new StreamBuildListener(System.out, Charset.defaultCharset());

        boolean testresult = condition.runPerform(build, listener);
        assertEquals(expected, testresult);
    }
}
