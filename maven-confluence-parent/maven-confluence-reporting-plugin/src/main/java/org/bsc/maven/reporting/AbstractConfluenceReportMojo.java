package org.bsc.maven.reporting;

import java.io.File;
import java.util.Collections;

import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.bsc.maven.plugin.confluence.ConfluenceUtils;
import org.codehaus.swizzle.confluence.Confluence;
import org.codehaus.swizzle.confluence.Page;
import org.jfrog.maven.annomojo.annotations.MojoParameter;

import biz.source_code.miniTemplator.MiniTemplator;
import biz.source_code.miniTemplator.MiniTemplator.VariableNotDefinedException;
import java.io.FilenameFilter;
import org.codehaus.swizzle.confluence.Attachment;
import org.jfrog.maven.annomojo.annotations.MojoSince;

/**
 * 
 * @author bsorrentino
 */
//@MojoThreadSafe
public abstract class AbstractConfluenceReportMojo extends AbstractMavenReport {
	
    @MojoParameter(description = "additional properties pass to template processor")
    private java.util.Map properties;
    /**
     * Confluence end point url 
     */
    @MojoParameter(expression = "${confluence.endPoint}", defaultValue = "http://localhost:8080/rpc/xmlrpc")
    private String endPoint;
    /**
     * Confluence target confluence's spaceKey 
     */
    @MojoParameter(expression = "${confluence.spaceKey}", required = true)
    private String spaceKey;
    /**
     * Confluence target confluence's spaceKey 
     */
    @MojoParameter(expression = "${confluence.parentPage}", defaultValue = "Home")
    private String parentPageTitle;
    /**
     * Confluence username 
     */
    @MojoParameter(expression = "${confluence.userName}", defaultValue = "admin")
    private String username;
    /**
     * Confluence password 
     */
    @MojoParameter(expression = "${confluence.password}", required = true)
    private String password;
    
    /**
     * 
     */
    @MojoParameter(expression = "${project}", readonly = true, required = true)
    protected MavenProject project;
    
    /**
     * 
     */
    @MojoParameter(defaultValue = "${basedir}/src/site/confluence/template.wiki", description = "MiniTemplator source. Default location is ${basedir}/src/site/confluence")
    protected java.io.File templateWiki;
    
    /**
     * 
     */
    @MojoParameter(description = "child pages - &lt;child&gt;&lt;name/&gt;[&lt;source/&gt]&lt;/child&gt")
    private java.util.List children;
    
    /**
     * 
     */
    @MojoParameter(description = "attachment folder", defaultValue = "${basedir}/src/site/confluence/attachments")
    private java.io.File attachmentFolder;
    
    /**
     * 
     */
    @MojoParameter(description = "children folder", defaultValue = "${basedir}/src/site/confluence/children")
    private java.io.File childrenFolder;
    
    /**
     * 
     */
    @MojoParameter(expression = "${confluence.removeSnapshots}",
    required = false,
    defaultValue = "false",
    description = "During publish of documentation related to a new release, if it's true, the pages related to SNAPSHOT will be removed ")
    protected boolean removeSnapshots = false;
    
    
    /**
     * @parameter expression="${settings}"
     * @readonly
     * @since 3.1.1
     */
    @MojoParameter(readonly = true, expression = "${settings}")
    protected org.apache.maven.settings.Settings mavenSettings;

    /**
     * 
     * @since 3.1.3
     */
    
    @MojoParameter(expression = "${project.build.finalName}",
    required = false,
    description = "Confluence Page Title - since 3.1.3")
    private String title;

    /**
     * 
     */
    public AbstractConfluenceReportMojo() {
        children = Collections.emptyList();
    }

    protected final String getTitle() {
        return title;
    }
    
    @SuppressWarnings("unchecked")
    public final java.util.Map<String, String> getProperties() {
        if (null == properties) {
            properties = new java.util.HashMap<String, String>(5);
        }
        return properties;
    }

    public final String getEndPoint() {
        return endPoint;
    }

    public final String getSpaceKey() {
        return spaceKey;
    }

    public final String getParentPageTitle() {
        return parentPageTitle;
    }

    public final String getUsername() {
        return username;
    }

    public final String getPassword() {
        return password;
    }

    @Override
    public MavenProject getProject() {
        return project;
    }

    public boolean isRemoveSnapshots() {
        return removeSnapshots;
    }

    public boolean isSnapshot() {
        final String version = project.getVersion();

        return (version != null && version.endsWith("-SNAPSHOT"));

    }

    public void addProperties(MiniTemplator t) {
        java.util.Map<String, String> properties = getProperties();

        if (properties == null || properties.isEmpty()) {
            getLog().info("no properties set!");
        } else {
            for (java.util.Map.Entry<String, String> e : properties.entrySet()) {
                getLog().debug(String.format("property %s = %s", e.getKey(), e.getValue()));

                try {
                    t.setVariable(e.getKey(), e.getValue(), true /* isOptional */);
                } catch (VariableNotDefinedException e1) {
                    getLog().warn(String.format("variable %s not defined in template", e.getKey()));
                }
            }
        }

    }

    private boolean generateChild(Confluence confluence, String spaceKey, String parentPageTitle, String titlePrefix, Child child) {

        java.io.File source = child.getSource(getProject());

        getLog().info(child.toString());

        try {

            if (!isSnapshot() && isRemoveSnapshots()) {
                final String snapshot = titlePrefix.concat("-SNAPSHOT");
                boolean deleted = ConfluenceUtils.removePage(confluence, spaceKey, parentPageTitle, snapshot);

                if (deleted) {
                    getLog().info(String.format("Page [%s] has been removed!", snapshot));
                }
            }

            final MiniTemplator t = new MiniTemplator(new java.io.FileReader(source));

            Page p = ConfluenceUtils.getOrCreatePage(confluence, spaceKey, parentPageTitle, String.format("%s - %s", titlePrefix, child.getName()));

            addProperties(t);

            p.setContent(t.generateOutput());

            p = confluence.storePage(p);

            return true;

        } catch (Exception e) {
            final String msg = "error loading template";
            getLog().error(msg, e);
            //throw new MavenReportException(msg, e);

            return false;
        }

    }

    /**
     * 
     * 
     */
    @SuppressWarnings("unchecked")
    protected void generateChildren(final Confluence confluence, final String spaceKey, final String parentPageTitle, final String titlePrefix) /*throws MavenReportException*/ {

        getLog().info(String.format("generateChildren # [%d]", children.size()));

        for (Child child : (java.util.List<Child>) children) {

            generateChild(confluence, spaceKey, parentPageTitle, titlePrefix, child);
        }

        if (childrenFolder.exists() && childrenFolder.isDirectory()) {

            childrenFolder.listFiles(new FilenameFilter() {

                public boolean accept(File file, String fileName) {

                    if (!file.isFile() && !file.canRead()) {
                        return false;
                    }
                    if (!fileName.endsWith(".wiki")) {
                        return false;
                    }


                    Child child = new Child();

                    child.setName(fileName.substring(0, fileName.length() - 5));
                    child.setSource(new File(file, fileName));

                    return generateChild(confluence, spaceKey, parentPageTitle, titlePrefix, child);

                }
            });
        }

    }

    protected void generateAttachments(Confluence confluence, Page page) /*throws MavenReportException*/ {

        getLog().info(String.format("generateAttachments pageId [%s]", page.getId()));

        java.io.File[] files = attachmentFolder.listFiles();

        if (files == null || files.length == 0) {
            getLog().info(String.format("No attachments found in folder [%s] ", attachmentFolder.getPath()));
            return;
        }

        final String version = "0";
        for (java.io.File f : files) {

            if (f.isDirectory() || f.isHidden()) {
                continue;
            }

            Attachment a = null;

            try {
                a = confluence.getAttachment(page.getId(), f.getName(), version);
            } catch (Exception e) {
                getLog().warn(String.format("Error getting attachment [%s] from confluence: [%s]", f.getName(), e.getMessage()));
            }

            if (a != null) {


                java.util.Date date = a.getCreated();

                if (date == null) {
                    getLog().warn(String.format("creation date of attachments [%s] is undefined. It will be replaced! ", a.getFileName()));
                } else {
                    if (f.lastModified() > date.getTime()) {
                        getLog().info(String.format("attachment [%s] is more recent than the remote one. It will be replaced! ", a.getFileName()));
                    } else {
                        getLog().info(String.format("attachment [%s] skipped! no updated detected", a.getFileName()));
                        continue;

                    }
                }
            } else {
                a = new Attachment();
                a.setComment("attached by maven-confluence-plugin");
                a.setFileName(f.getName());
                a.setContentType("application/octet-stream");

            }

            try {
                ConfluenceUtils.addAttchment(confluence, page, a, f);
            } catch (Exception e) {
                getLog().error(String.format("Error uploading attachment [%s] ", f.getName()), e);
            }

        }

    }
}