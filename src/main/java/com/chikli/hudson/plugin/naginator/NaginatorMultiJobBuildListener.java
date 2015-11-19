package com.chikli.hudson.plugin.naginator;

import com.tikal.jenkins.plugins.multijob.MultiJobBuild;
import com.tikal.jenkins.plugins.multijob.listeners.MultiJobListener;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Result;

@Extension
public class NaginatorMultiJobBuildListener extends MultiJobListener {

	@Override
	public boolean isComplete(AbstractBuild<?, ?> build, MultiJobBuild multiJobBuild) {
		NaginatorPublisherScheduleAction action = build.getAction(NaginatorPublisherScheduleAction.class);
		if (null != action) {
			Result result = build.getResult();
			int retryCount = NaginatorListener.calculateRetryCount(build);
			if (null != result && retryCount < action.getMaxSchedule()) {
				boolean f = (result.equals(Result.UNSTABLE) && action.isRerunIfUnstable())
						|| result.equals(Result.FAILURE);
				return !f;
			}
		}

		return true;
	}
}
