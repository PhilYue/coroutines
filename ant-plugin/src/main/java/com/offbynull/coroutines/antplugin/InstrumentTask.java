/*
 * Copyright (c) 2018, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.coroutines.antplugin;

import com.offbynull.coroutines.instrumenter.InstrumentationSettings;
import com.offbynull.coroutines.instrumenter.Instrumenter;
import com.offbynull.coroutines.instrumenter.PluginHelper;
import com.offbynull.coroutines.instrumenter.generators.DebugGenerators.MarkerType;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/**
 * ANT task to run coroutine instrumentation.
 * <p>
 * Sample usage in build script:
 * <pre>
 *    &lt;taskdef name="InstrumentTask" classname="com.offbynull.coroutines.antplugin.InstrumentTask"&gt;
 *        &lt;classpath&gt;
 *            &lt;pathelement location="ant-plugin-{version}-shaded.jar"/&gt;
 *        &lt;/classpath&gt;
 *    &lt;/taskdef&gt;
 *    
 *    &lt;target name="-post-compile"&gt;
 *        &lt;InstrumentTask classpath="somelib.jar;somefolder;someotherlib.jar" sourceDirectory="build" targetDirectory="build"/&gt;
 *    &lt;/target&gt;
 * </pre>
 *
 * @author Kasra Faghihi
 */
public final class InstrumentTask extends Task {

    private String markerType = MarkerType.NONE.name();
    
    private boolean debugMode = false;

    private boolean autoSerializable = true;

    private String classpath;

    private File sourceDirectory;
    
    private File targetDirectory;

    /**
     * Constructs a {@link InstrumentTask} object.
     */
    public InstrumentTask() {
        classpath = "";
    }

    /**
     * Sets the marker type. Defaults to NONE.
     * @param markerType debug marker type (must be a value from {@link MarkerType})
     */
    public void setMarkerType(String markerType) {
        this.markerType = markerType;
    }

    /**
     * Sets the debug mode. Defaults to {@code false}.
     * @param debugMode debug mode
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    /**
     * Sets the auto-serializable flag. Defaults to {@code true}.
     * @param autoSerializable auto-serializable
     */
    public void setAutoSerializable(boolean autoSerializable) {
        this.autoSerializable = autoSerializable;
    }

    /**
     * Sets the classpath -- required by instrumenter when instrumenting class files.
     * @param classpath semicolon delimited classpath
     */
    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    /**
     * Sets the directory to read class files from.
     * @param sourceDirectory source directory
     */
    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    /**
     * Sets the directory to write instrumented class files to.
     * @param targetDirectory target directory
     */
    public void setTargetDirectory(File targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    @Override
    public void execute() throws BuildException {
        // Check classpath
        if (classpath == null) {
            throw new BuildException("Classpath not set");
        }

        // Check source directory
        if (sourceDirectory == null) {
            throw new BuildException("Source directory not set");
        }
        if (!sourceDirectory.isDirectory()) {
            throw new BuildException("Source directory is not a directory: " + sourceDirectory.getAbsolutePath());
        }
        
        // Check target directory
        if (targetDirectory == null) {
            throw new BuildException("Target directory not set");
        }
        try {
            FileUtils.forceMkdir(targetDirectory);
        } catch (IOException ioe) {
            throw new BuildException("Unable to create target directory", ioe);
        }

        // Check instrumentation settings (sanity check, should default to null)
        if (markerType == null) {
            throw new BuildException("Marker type not set");
        }

        List<File> combinedClasspath;
        try {
            log("Getting compile classpath", Project.MSG_DEBUG);
            combinedClasspath = Arrays.stream(classpath.split(";"))
                    .map(x -> x.trim())
                    .filter(x -> !x.isEmpty())
                    .map(x -> new File(x))
                    .collect(Collectors.toList());

            log("Classpath for instrumentation is as follows: " + combinedClasspath, Project.MSG_DEBUG);
        } catch (Exception ex) {
            throw new BuildException("Unable to get compile classpath elements", ex);
        }

        Instrumenter instrumenter;
        try {
            log("Creating instrumenter...", Project.MSG_DEBUG);
            MarkerType markerTypeEnum = MarkerType.valueOf(markerType);
            instrumenter = new Instrumenter(combinedClasspath);
            InstrumentationSettings settings = new InstrumentationSettings(markerTypeEnum, debugMode, autoSerializable);
            
            log("Processing " + sourceDirectory.getAbsolutePath() + " ... ", Project.MSG_DEBUG);
            PluginHelper.instrument(instrumenter, settings, sourceDirectory, targetDirectory, this::log);
        } catch (Exception ex) {
            throw new BuildException("Failed to instrument", ex);
        }
    }
}
