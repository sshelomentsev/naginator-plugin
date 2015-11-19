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
		boolean childIsPassed = true;
		NaginatorPublisherScheduleAction action = build.getAction(NaginatorPublisherScheduleAction.class);
		if (null != action) {
			Result result = build.getResult();
			int retryCount = NaginatorListener.calculateRetryCount(build);
			if (null != result && retryCount < action.getMaxSchedule()) {
				boolean f = (result.equals(Result.UNSTABLE) && action.isRerunIfUnstable())
						|| result.equals(Result.FAILURE);
				childIsPassed = !f;
			}
		}

		if (childIsPassed) {
			AbstractBuild<?, ?> parentBuild = multiJobBuild;
			NaginatorPublisherScheduleAction parentAction = parentBuild.getAction(NaginatorPublisherScheduleAction
					.class);
			if (null != parentAction) {
				System.out.println("parent action for " + build.getProject().getDisplayName() + " #" + build
						.getNumber());
				Result result = build.getResult();
				if (null != result && parentAction.isRerunMultiJobChild()) {
					boolean f = (result.equals(Result.UNSTABLE) && parentAction.isRerunIfUnstable())
							|| result.equals(Result.FAILURE);
					System.out.println("ans = " + !f);
					return !f;
				}
			}
		}
		return childIsPassed;
	}
}
