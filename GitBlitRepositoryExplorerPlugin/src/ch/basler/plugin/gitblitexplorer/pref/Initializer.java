package ch.basler.plugin.gitblitexplorer.pref;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import ch.basler.plugin.gitblitexplorer.main.Activator;

/**
 * @author MicBag
 *
 */
public class Initializer extends AbstractPreferenceInitializer {

	public Initializer() {
	}

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
	    store.setDefault(GitBlitExplorerPrefPage.KEY_GITBLIT_USER, System.getProperty("user.name"));
	    store.setDefault(GitBlitExplorerPrefPage.KEY_GITBLIT_DCLICK, GitBlitExplorerPrefPage.VALUE_GITBLIT_DCLICK_GITBLIT);
	}

}
