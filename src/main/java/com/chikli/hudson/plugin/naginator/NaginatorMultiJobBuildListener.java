package com.chikli.hudson.plugin.naginator;

import com.tikal.jenkins.plugins.multijob.MultiJobBuild;
import com.tikal.jenkins.plugins.multijob.listeners.MultiJobListener;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;

@Extension
public class NaginatorMultiJobBuildListener extends MultiJobListener {

	@Override
	public void onStart(AbstractBuild<?, ?> build, MultiJobBuild multiJobBuild) {
		AbstractBuild<?, ?> parentBuild = multiJobBuild;
		AbstractProject<?, ?> parentProject = parentBuild.getProject();
		NaginatorPublisher np = parentProject.getPublishersList().get(NaginatorPublisher.class);
		NaginatorPublisherScheduleAction action = build.getAction(NaginatorPublisherScheduleAction.class);
		if (null != np && null == action) {
			NaginatorPublisherScheduleAction childAction = new NaginatorPublisherScheduleAction(np);
			build.addAction(childAction);
		}
	}

	@Override
	public boolean isComplete(AbstractBuild<?, ?> build, MultiJobBuild multiJobBuild) {
		boolean childIsPassed = true;
		NaginatorPublisherScheduleAction action = build.getAction(NaginatorPublisherScheduleAction.class);
		if (null != action) {
			Result result = build.getResult();
			int retryCount = NaginatorListener.calculateRetryCount(build);
			if (null != result && retryCount < action.getMaxSchedule()) {
				boolean f = (result.equals(Result.UNSTABLE) && action.isRerunIfUnstable())
						|| result.equals(Result.FAILURE);
				childIsPassed = !f;
			} else {
			}
		}

		return childIsPassed;
	}
}
