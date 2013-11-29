package ch.basler.plugin.gitblitexplorer.view;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import ch.basler.plugin.gitblitexplorer.common.GitBlitBD;
import ch.basler.plugin.gitblitexplorer.common.GitBlitRepository;
import ch.basler.plugin.gitblitexplorer.main.Activator;
import ch.basler.plugin.gitblitexplorer.pref.GitBlitExplorerPrefPage;

/**
 * @author MicBag
 *
 */
public class RepoExplorerView extends ViewPart {

	private TreeViewer viewer;
	private List<RepoViewModel> rootModel;

	private boolean isDoubleClickGit = false;
	
	// ------------------------------------------------------------------------
	// --- Actions
	// ------------------------------------------------------------------------

	// ------------------------------------------------------------------------
	// Copy selected Project to clipboard
	Action actionCopyClipboard = new Action("Copy Repository URL") {
		@Override
		public void run() {
			IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
			if (selection != null) {
				RepoViewModel model = (RepoViewModel) selection.getFirstElement();
				if (model != null) {
					Clipboard clipboard = new Clipboard(viewer.getControl().getDisplay());
					clipboard.setContents(new Object[] { model.endpoint }, new Transfer[] { TextTransfer.getInstance() });
				}
			}
		}

		@Override
		public ImageDescriptor getImageDescriptor() {
			return RepoExplorerView.getImageDescriptor(ISharedImages.IMG_TOOL_COPY);
		}
	};

	// ------------------------------------------------------------------------
	// Open gitblit summary
	private Action actionOpenGitBlit = new Action("Open GitBlit") {
		@Override
		public void run() {
			IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
			if (selection != null) {
				RepoViewModel model = (RepoViewModel) selection.getFirstElement();
				if (model != null) {
					try {
						IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
						String url = preferenceStore.getString(GitBlitExplorerPrefPage.KEY_GITBLIT_URL);
						url = url + "/summary/" + model.repo + "!" + model.project + ".git";
						PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(url));
					} catch (Exception e) {
						logError(e.toString());
					}
				}
			}
		}

		@Override
		public ImageDescriptor getImageDescriptor() {
			return RepoExplorerView.getImageDescriptor(ISharedImages.IMG_TOOL_COPY);
		}
	};

	
	// ------------------------------------------------------------------------
	// Separate instance to unregiter listener at dispose cycle
	IPropertyChangeListener propChangeListener = new IPropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent event) {
			String prop = event.getProperty();
			if (prop != null && prop.startsWith("gitblit.") && viewer != null) {
				rootModel = readRepositories();
				viewer.setInput(rootModel);
				initDoubleBehaviour();
			}
		}
	};
	
	// ------------------------------------------------------------------------
	// ------------------------------------------------------------------------
	public RepoExplorerView() {
	}

	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#dispose()
	 */
	@Override
	public void dispose() {
		super.dispose();
		Activator.getDefault().getPreferenceStore().removePropertyChangeListener(propChangeListener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets
	 * .Composite)
	 */
	@Override
	public void createPartControl(Composite parent) {
		// --------------------------------------------------------------------
		// Sync preferences
		// --------------------------------------------------------------------
		initDoubleBehaviour();
		Activator.getDefault().getPreferenceStore().addPropertyChangeListener(propChangeListener);
		checkPreferences();
		
		// --------------------------------------------------------------------
		// Layout
		// --------------------------------------------------------------------
		GridLayout l = GridLayoutFactory.swtDefaults().create();
		GridData gd = GridDataFactory.swtDefaults().create();
		l.numColumns = 1;
		gd.grabExcessHorizontalSpace = true;
		gd.grabExcessVerticalSpace = true;
		gd.horizontalAlignment = SWT.FILL;
		gd.verticalAlignment = SWT.FILL;
		parent.setLayout(l);
		parent.setLayoutData(gd);

		Composite comp = new Composite(parent, SWT.NONE);
		l = GridLayoutFactory.swtDefaults().create();
		l.numColumns = 2;
		gd = GridDataFactory.swtDefaults().create();
		gd.grabExcessHorizontalSpace = true;
		gd.horizontalAlignment = SWT.FILL;
		comp.setLayout(l);
		comp.setLayoutData(gd);

		// --------------------------------------------------------------------
		// Project search field
		// --------------------------------------------------------------------
		Label label = new Label(comp, SWT.NONE);
		label.setText("Search project: ");

		final Text text = new Text(comp, SWT.SINGLE | SWT.BORDER);
		gd = GridDataFactory.swtDefaults().create();
		gd.grabExcessHorizontalSpace = true;
		gd.horizontalAlignment = SWT.FILL;
		text.setLayoutData(gd);

		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				filterResult(text.getText());
			}
		});
		
		text.addKeyListener(new KeyListener() {
			// Because of a binding conflict, plugin key binding will not work 
			@Override
			public void keyReleased(KeyEvent e) {
				if(e.keyCode == SWT.ARROW_DOWN || e.keyCode == SWT.PAGE_DOWN){
					viewer.getControl().setFocus();
				}
			}
			@Override
			public void keyPressed(KeyEvent e) {
			}
		});		

		// --------------------------------------------------------------------
		// Tree viewer
		// --------------------------------------------------------------------
		viewer = new TreeViewer(parent, SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.setContentProvider(new RepoContentProvider());
		viewer.setLabelProvider(new RepoLabelProvider());
		rootModel = readRepositories();
		viewer.setInput(rootModel);

		l = GridLayoutFactory.swtDefaults().create();
		gd.grabExcessHorizontalSpace = true;
		gd.grabExcessVerticalSpace = true;
		gd.horizontalAlignment = SWT.FILL;
		gd.verticalAlignment = SWT.FILL;
		viewer.getControl().setLayoutData(gd);

		// viewer.getTree().setHeaderVisible(true);
		// TreeColumn colRepo= new TreeColumn(viewer.getTree(), SWT.LEFT);
		// viewer.getTree().setLinesVisible(true);
		// colRepo.setAlignment(SWT.LEFT);
		// colRepo.setText("Repository / Project");
		// colRepo.setWidth(320);
		//
		// TreeColumn colDesc = new TreeColumn(viewer.getTree(), SWT.LEFT);
		// colDesc.setAlignment(SWT.LEFT);
		// colDesc.setText("Description");
		// colDesc.setWidth(320);

		// --------------------------------------------------------------------
		// Adding viewer interaction
		// --------------------------------------------------------------------
		int operations = DND.DROP_COPY | DND.DROP_MOVE;
		Transfer[] transferTypes = new Transfer[] { TextTransfer.getInstance() };
		viewer.addDragSupport(operations, transferTypes, new RepoDragListener(viewer));

		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				getDoubleClickAction(true).run();
			}
		});

		final MenuManager mgr = new MenuManager();
		mgr.setRemoveAllWhenShown(true);

		mgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
				if (selection != null) {
					RepoViewModel model = (RepoViewModel) selection.getFirstElement();
					if (model != null && model.endpoint != null && model.endpoint.trim().isEmpty() == false) {
						mgr.add(getDoubleClickAction(false));
					}
				}
			}
		});

		viewer.getControl().setMenu(mgr.createContextMenu(viewer.getControl()));
	}
	

	/**
	 * Filtering viewer model passed project name
	 * 
	 * @param search
	 */
	private void filterResult(String search) {
		if (search == null || search.trim().isEmpty()) {
			viewer.setInput(rootModel);
			viewer.collapseAll();
			return;
		}
		final String sarg = search.toLowerCase().trim();
		BusyIndicator.showWhile(Display.getDefault(), new Runnable() {
			public void run() {
				List<RepoViewModel> resList = new ArrayList<RepoViewModel>();
				List<RepoViewModel> clist;
				RepoViewModel model;
				for (RepoViewModel item : rootModel) {
					if (item.getChilds().size() > 0) {
						clist = item.getChilds();
						model = null;
						for (RepoViewModel citem : clist) {
							if (citem.project != null && citem.project.toLowerCase().contains(sarg)) {
								if (model == null) {
									model = new RepoViewModel(item);
									resList.add(model);
								}
								model.add(new RepoViewModel(citem));
							}
						}
					}
				}
				viewer.setInput(resList);
				viewer.expandAll();
			}
		});
	}

	private final static Image getImage(String name) {
		return Activator.getDefault().getImageRegistry().get(name);
	}

	private final static ImageDescriptor getImageDescriptor(String name) {
		return Activator.getDefault().getImageRegistry().getDescriptor(name);
	}

	/**
	 * Init double click behaviour based on preference settings
	 */
	private void initDoubleBehaviour() {
		IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
		String value = preferenceStore.getString(GitBlitExplorerPrefPage.KEY_GITBLIT_DCLICK);
		if (GitBlitExplorerPrefPage.VALUE_GITBLIT_DCLICK_GIT.equalsIgnoreCase(value)) {
			isDoubleClickGit = true;
		} else {
			isDoubleClickGit = false;
		}
	}

	private class Holder<T> {
		public T value;

		public Holder(T value) {
			this.value = value;
		}
	};

	/**
	 * Reading repos / projects from gitblit
	 * 
	 * @return
	 */
	private List<RepoViewModel> readRepositories() {
		final Holder<Boolean> noAccess = new Holder<Boolean>(Boolean.FALSE);

		final List<RepoViewModel> modelList = new ArrayList<RepoViewModel>();
		BusyIndicator.showWhile(Display.getDefault(), new Runnable() {
			public void run() {
				try {

					IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
					String url = preferenceStore.getString(GitBlitExplorerPrefPage.KEY_GITBLIT_URL);
					String user = preferenceStore.getString(GitBlitExplorerPrefPage.KEY_GITBLIT_USER);
					String pwd = preferenceStore.getString(GitBlitExplorerPrefPage.KEY_GITBLIT_PWD);
					url = url == null ? "" : url.trim();
					user = user == null ? "" : user.trim();
					pwd = pwd == null ? "" : pwd.trim();

					if (url == "" || user == "" || pwd == "") {
						noAccess.value = true;
						return;
					}

					// --------------------------------------------------------
					// Call GitBlit
					// --------------------------------------------------------
					GitBlitBD bd = new GitBlitBD(url, user, pwd, true);
					Map<String, List<GitBlitRepository>> map = bd.readRepositoryMap();
					RepoViewModel model;
					List<GitBlitRepository> list;
					for (String repo : map.keySet()) {
						model = new RepoViewModel();
						model.repo = repo;
						modelList.add(model);
						list = map.get(repo);
						for (GitBlitRepository item : list) {
							model.add(new RepoViewModel(item.url, item.repositoryName, item.projectName, item.description));
						}
					}
				} catch (Exception e) {
					logError(e.toString());
				}
			}
		});
		if (noAccess.value) {
			modelList.add(new RepoViewModel(null, null, "Please configure GitBlit Explorer in preferences.", null));
		}
		return modelList;
	}

	@Override
	public void setFocus() {
	}

	/**
	 * Determinate double click action, based on preferences
	 * 
	 * @param dclick
	 * @return
	 */
	public Action getDoubleClickAction(boolean dclick) {
		if (dclick) {
			if (isDoubleClickGit) {
				return actionCopyClipboard;
			} else {
				return actionOpenGitBlit;
			}
		} else {
			if (isDoubleClickGit) {
				return actionOpenGitBlit;
			} else {
				return actionCopyClipboard;
			}
		}
	}
	
	private void checkPreferences(){
		IPreferenceStore preferenceStore = Activator.getDefault().getPreferenceStore();
		String value = preferenceStore.getString(GitBlitExplorerPrefPage.KEY_GITBLIT_URL);
		try{
			new URL(value);
		}
		catch(Exception e){
			logError(value);
			showMessage(IStatus.ERROR,"GitBlit Explorer: Configuration Error", "GitBlit URL is not specified or invalid");
		}
		value = preferenceStore.getString(GitBlitExplorerPrefPage.KEY_GITBLIT_USER);
		if(value == null || value.trim().isEmpty()){
			String msg = "Missing configuration parameter: User";
			logError(value);
			showMessage(IStatus.ERROR,"GitBlit Explorer: Configuration Error", msg);
		}

		value = preferenceStore.getString(GitBlitExplorerPrefPage.KEY_GITBLIT_PWD);
		if(value == null || value.trim().isEmpty()){
			String msg = "Missing configuration parameter: Password";
			logError(value);
			showMessage(IStatus.ERROR,"GitBlit Explorer: Configuration Error", msg);
		}
	}

	private void logError(String msg) {
		IStatus s = new Status(IStatus.ERROR, Activator.PLUGIN_ID, msg);
		Activator.getDefault().getLog().log(s);
	}
	
	private void showMessage(final int level, final String title, final String msg){
		getSite().getShell().getDisplay().asyncExec(new Runnable() {
	        public void run() {
	        	MessageDialog.open(level,getSite().getShell(),title,msg,SWT.NONE);
	        }
	    });
	}

}
