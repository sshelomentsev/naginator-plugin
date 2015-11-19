package com.chikli.hudson.plugin.naginator;

import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Reschedules a build if the current one fails.
 *
 * @author Nayan Hajratwala <nayan@chikli.com>
 */
public class NaginatorPublisher extends Notifier {
    private final String regexpForRerun;
    private final boolean rerunIfUnstable;
    private final boolean rerunMatrixPart;
    private final boolean rerunMultiJobChild;
    private final boolean checkRegexp;
    private final Boolean regexpForMatrixParent;

    private ScheduleDelay delay;

    private int maxSchedule;

    // backward compatible constructor
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean checkRegexp) {
        this(regexpForRerun, rerunIfUnstable, false, checkRegexp, 0, new ProgressiveDelay(5*60, 3*60*60));
    }

    /**
     * backward compatible constructor.
     * 
     * Watch that <code>regexpForMatrixParent</code> gets <code>true</code>
     * for the backward compatibility.
     */
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean rerunMatrixPart,
                              boolean checkRegexp,
                              int maxSchedule,
                              ScheduleDelay delay) {
        this(regexpForRerun, rerunIfUnstable, rerunMatrixPart, false, checkRegexp, true, maxSchedule, delay);
    }

	/**
	 * backward compatible constructor
     */
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean rerunMatrixPart,
                              boolean checkRegexp,
                              boolean regexpForMatrixParent,
                              int maxSchedule,
                              ScheduleDelay delay) {
        this(regexpForRerun, rerunIfUnstable, rerunMatrixPart, false, checkRegexp, regexpForMatrixParent,
            maxSchedule, delay);
    }

    /**
     * @since 1.17
     */
    @DataBoundConstructor
    public NaginatorPublisher(String regexpForRerun,
                              boolean rerunIfUnstable,
                              boolean rerunMatrixPart,
                              boolean rerunMultiJobChild,
                              boolean checkRegexp,
                              boolean regexpForMatrixParent,
                              int maxSchedule,
                              ScheduleDelay delay) {
        this.regexpForRerun = regexpForRerun;
        this.rerunIfUnstable = rerunIfUnstable;
        this.rerunMatrixPart = rerunMatrixPart;
        this.rerunMultiJobChild = rerunMultiJobChild;
        this.checkRegexp = checkRegexp;
        this.maxSchedule = maxSchedule;
        this.regexpForMatrixParent = regexpForMatrixParent;
        this.delay = delay;
    }

    public Object readResolve() {
        if (this.delay == null) {
            // Backward compatibility : progressive 5 minutes up to 3 hours
            delay = new ProgressiveDelay(5*60, 3*60*60);
        }
        if (regexpForMatrixParent == null) {
            return new NaginatorPublisher(
                    regexpForRerun,
                    rerunIfUnstable,
                    rerunMatrixPart,
                    checkRegexp,
                    true,               // true for backward compatibility.
                    maxSchedule,
                    delay
            );
        }
        return this;
    }

    public boolean isRerunIfUnstable() {
        return rerunIfUnstable;
    }

    public boolean isRerunMatrixPart() {
        return rerunMatrixPart;
    }

    public boolean isRerunMultiJobChild() {
        return rerunMultiJobChild;
    }

    public boolean isCheckRegexp() {
        return checkRegexp;
    }

    /**
     * Returns whether apply the regexp to the matrix parent instead of matrix children.
     * 
     * The default is <code>false</code> for naginator-plugin >= 1.16
     * though <code>true</code> for configurations upgraded from naginator-plugin < 1.16.
     * 
     * @return Returns whether apply the regexp to the matrix parent instead of matrix children
     * @since 1.16
     */
    public boolean isRegexpForMatrixParent() {
        return regexpForMatrixParent;
    }

    public String getRegexpForRerun() {
        return regexpForRerun;
    }

    public ScheduleDelay getDelay() {
        return delay;
    }

    public int getMaxSchedule() {
        return maxSchedule;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (build instanceof MatrixRun) {
            MatrixBuild parent = ((MatrixRun)build).getParentBuild();
            if (parent.getAction(NaginatorPublisherScheduleAction.class) == null) {
                // No strict exclusion is required
                // as it doesn't matter if the action gets duplicated.
                parent.addAction(new NaginatorPublisherScheduleAction(this));
            }
        } else {
            build.addAction(new NaginatorPublisherScheduleAction(this));
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public final static NaginatorListener LISTENER = new NaginatorListener();


    /**
     * Descriptor for {@link NaginatorPublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>views/hudson/plugins/naginator/NaginatorBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private boolean isApplicableForMultiJob = false;

        public DescriptorImpl() {
            super(NaginatorPublisher.class);
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Retry build after failure";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            if (jobType.equals(MultiJobProject.class)) {
                isApplicableForMultiJob = true;
            }
            return true;
        }

        public boolean isMultiJobProject() {
            return isApplicableForMultiJob;
        }

        /**
         * Creates a new instance of {@link NaginatorPublisher} from a submitted form.
         */
        @Override
        public Notifier newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(NaginatorPublisher.class, formData);
        }
        
        /**
         * @return true if the current request is for a matrix project.
         * @since 1.16
         */
        public boolean isMatrixProject() {
            StaplerRequest req = Stapler.getCurrentRequest();
            if (req == null) {
                return false;
            }
            Job<?, ?> job = req.findAncestorObject(Job.class);
            return (job instanceof MatrixProject);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(NaginatorPublisher.class.getName());

}
