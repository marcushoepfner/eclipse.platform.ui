package org.eclipse.ui.internal.menus;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionConverter;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.e4.core.commands.ECommandService;
import org.eclipse.e4.ui.internal.workbench.ContributionsAnalyzer;
import org.eclipse.e4.ui.internal.workbench.swt.Policy;
import org.eclipse.e4.ui.internal.workbench.swt.WorkbenchSWTActivator;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.commands.MCommand;
import org.eclipse.e4.ui.model.application.commands.impl.CommandsFactoryImpl;
import org.eclipse.e4.ui.model.application.ui.MCoreExpression;
import org.eclipse.e4.ui.model.application.ui.MExpression;
import org.eclipse.e4.ui.model.application.ui.impl.UiFactoryImpl;
import org.eclipse.e4.ui.model.application.ui.menu.ItemType;
import org.eclipse.e4.ui.model.application.ui.menu.MHandledMenuItem;
import org.eclipse.e4.ui.model.application.ui.menu.MHandledToolItem;
import org.eclipse.e4.ui.model.application.ui.menu.MMenu;
import org.eclipse.e4.ui.model.application.ui.menu.MMenuElement;
import org.eclipse.e4.ui.model.application.ui.menu.MRenderedMenu;
import org.eclipse.e4.ui.model.application.ui.menu.MToolBarElement;
import org.eclipse.e4.ui.model.application.ui.menu.impl.MenuFactoryImpl;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate2;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.internal.handlers.ActionDelegateHandlerProxy;
import org.eclipse.ui.internal.registry.IWorkbenchRegistryConstants;
import org.eclipse.ui.internal.util.Util;
import org.eclipse.ui.menus.CommandContributionItem;

public class MenuHelper {

	public static void trace(String msg, Throwable error) {
		WorkbenchSWTActivator.trace(Policy.MENUS, msg, error);
	}

	public static final String ACTION_SET_CMD_PREFIX = "AS::"; //$NON-NLS-1$
	public static final String MAIN_MENU_ID = "org.eclipse.ui.main.menu"; //$NON-NLS-1$
	private static Field urlField;

	public static String getActionSetCommandId(IConfigurationElement element) {
		String id = MenuHelper.getDefinitionId(element);
		if (id != null) {
			return id;
		}
		id = MenuHelper.getId(element);
		String actionSetId = null;
		Object obj = element.getParent();
		while (obj instanceof IConfigurationElement && actionSetId == null) {
			IConfigurationElement parent = (IConfigurationElement) obj;
			if (parent.getName().equals(IWorkbenchRegistryConstants.TAG_ACTION_SET)) {
				actionSetId = MenuHelper.getId(parent);
			}
			obj = parent.getParent();
		}
		return ACTION_SET_CMD_PREFIX + actionSetId + '/' + id;
	}

	/**
	 * @param imageDescriptor
	 * @return
	 */
	public static String getImageUrl(ImageDescriptor imageDescriptor) {
		if (imageDescriptor == null)
			return null;
		Class idc = imageDescriptor.getClass();
		if (idc.getName().endsWith("URLImageDescriptor")) { //$NON-NLS-1$
			URL url = getUrl(idc, imageDescriptor);
			return url.toExternalForm();
		}
		return null;
	}

	private static URL getUrl(Class idc, ImageDescriptor imageDescriptor) {
		try {
			if (urlField == null) {
				urlField = idc.getDeclaredField("url"); //$NON-NLS-1$
				urlField.setAccessible(true);
			}
			return (URL) urlField.get(imageDescriptor);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param element
	 *            the configuration element
	 * @return <code>true</code> if the checkEnabled is <code>true</code>.
	 */
	static boolean getVisibleEnabled(IConfigurationElement element) {
		IConfigurationElement[] children = element
				.getChildren(IWorkbenchRegistryConstants.TAG_VISIBLE_WHEN);
		String checkEnabled = null;

		if (children.length > 0) {
			checkEnabled = children[0].getAttribute(IWorkbenchRegistryConstants.ATT_CHECK_ENABLED);
		}

		return checkEnabled != null && checkEnabled.equalsIgnoreCase("true"); //$NON-NLS-1$
	}

	static MExpression getVisibleWhen(IConfigurationElement commandAddition) {
		try {
			IConfigurationElement[] visibleConfig = commandAddition
					.getChildren(IWorkbenchRegistryConstants.TAG_VISIBLE_WHEN);
			if (visibleConfig.length > 0 && visibleConfig.length < 2) {
				IConfigurationElement[] visibleChild = visibleConfig[0].getChildren();
				if (visibleChild.length > 0) {
					Expression visWhen = ExpressionConverter.getDefault().perform(visibleChild[0]);
					MCoreExpression exp = UiFactoryImpl.eINSTANCE.createCoreExpression();
					exp.setCoreExpressionId("programmatic.value"); //$NON-NLS-1$
					exp.setCoreExpression(visWhen);
					return exp;
					// visWhenMap.put(configElement, visWhen);
				}
			}
		} catch (InvalidRegistryObjectException e) {
			// visWhenMap.put(configElement, null);
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CoreException e) {
			// visWhenMap.put(configElement, null);
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/*
	 * Support Utilities
	 */
	public static String getId(IConfigurationElement element) {
		String id = element.getAttribute(IWorkbenchRegistryConstants.ATT_ID);

		// For sub-menu management -all- items must be id'd so enforce this
		// here (we could optimize by checking the 'name' of the config
		// element == "menu"
		if (id == null || id.length() == 0) {
			id = getCommandId(element);
		}
		if (id == null || id.length() == 0) {
			id = element.toString();
		}

		return id;
	}

	static String getName(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_NAME);
	}

	static int getMode(IConfigurationElement element) {
		if ("FORCE_TEXT".equals(element.getAttribute(IWorkbenchRegistryConstants.ATT_MODE))) { //$NON-NLS-1$
			return CommandContributionItem.MODE_FORCE_TEXT;
		}
		return 0;
	}

	static String getLabel(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_LABEL);
	}

	static String getPath(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_PATH);
	}

	static String getMenuBarPath(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_MENUBAR_PATH);
	}

	static String getToolBarPath(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_TOOLBAR_PATH);
	}

	static String getMnemonic(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_MNEMONIC);
	}

	static String getTooltip(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_TOOLTIP);
	}

	static String getIconPath(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_ICON);
	}

	static String getDisabledIconPath(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_DISABLEDICON);
	}

	static String getHoverIconPath(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_HOVERICON);
	}

	static String getIconUrl(IConfigurationElement element, String attr) {
		String extendingPluginId = element.getDeclaringExtension().getContributor().getName();

		String iconPath = element.getAttribute(attr);
		if (iconPath == null) {
			return null;
		}
		if (!iconPath.startsWith("platform:")) { //$NON-NLS-1$
			iconPath = "platform:/plugin/" + extendingPluginId + "/" + iconPath; //$NON-NLS-1$//$NON-NLS-2$
		}
		URL url = null;
		try {
			url = FileLocator.find(new URL(iconPath));
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return url == null ? null : url.toString();
	}

	static String getHelpContextId(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_HELP_CONTEXT_ID);
	}

	public static boolean isSeparatorVisible(IConfigurationElement element) {
		String val = element.getAttribute(IWorkbenchRegistryConstants.ATT_VISIBLE);
		return Boolean.valueOf(val).booleanValue();
	}

	public static String getClassSpec(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_CLASS);
	}

	public static String getCommandId(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_COMMAND_ID);
	}

	public static ItemType getStyle(IConfigurationElement element) {
		String style = element.getAttribute(IWorkbenchRegistryConstants.ATT_STYLE);
		if (style == null || style.length() == 0) {
			return ItemType.PUSH;
		}
		if (IWorkbenchRegistryConstants.STYLE_TOGGLE.equals(style)) {
			return ItemType.CHECK;
		}
		if (IWorkbenchRegistryConstants.STYLE_RADIO.equals(style)) {
			return ItemType.RADIO;
		}
		if (IWorkbenchRegistryConstants.STYLE_PULLDOWN.equals(style)) {
			trace("Failed to get style for " + IWorkbenchRegistryConstants.STYLE_PULLDOWN, null); //$NON-NLS-1$
			// return CommandContributionItem.STYLE_PULLDOWN;
		}
		return ItemType.PUSH;
	}

	public static boolean getRetarget(IConfigurationElement element) {
		String r = element.getAttribute(IWorkbenchRegistryConstants.ATT_RETARGET);
		return Boolean.valueOf(r);
	}

	public static String getDefinitionId(IConfigurationElement element) {
		return element.getAttribute(IWorkbenchRegistryConstants.ATT_DEFINITION_ID);
	}

	public static Map<String, String> getParameters(IConfigurationElement element) {
		HashMap<String, String> map = new HashMap<String, String>();
		IConfigurationElement[] parameters = element
				.getChildren(IWorkbenchRegistryConstants.TAG_PARAMETER);
		for (int i = 0; i < parameters.length; i++) {
			String name = parameters[i].getAttribute(IWorkbenchRegistryConstants.ATT_NAME);
			String value = parameters[i].getAttribute(IWorkbenchRegistryConstants.ATT_VALUE);
			if (name != null && value != null) {
				map.put(name, value);
			}
		}
		return map;
	}

	public static MMenu createMenuAddition(IConfigurationElement menuAddition) {
		MMenu element = MenuFactoryImpl.eINSTANCE.createMenu();
		String id = MenuHelper.getId(menuAddition);
		element.setElementId(id);
		String text = MenuHelper.getLabel(menuAddition);
		String mnemonic = MenuHelper.getMnemonic(menuAddition);
		if (text != null && mnemonic != null) {
			int idx = text.indexOf(mnemonic);
			if (idx != -1) {
				text = text.substring(0, idx) + '&' + text.substring(idx);
			}
		}
		element.setIconURI(MenuHelper
				.getIconUrl(menuAddition, IWorkbenchRegistryConstants.ATT_ICON));
		element.setLabel(Util.safeString(text));

		return element;
	}

	public static MMenuElement createLegacyMenuActionAdditions(MApplication app,
			final IConfigurationElement element) {
		final String id = MenuHelper.getId(element);
		String text = MenuHelper.getLabel(element);
		String mnemonic = MenuHelper.getMnemonic(element);
		if (text != null && mnemonic != null) {
			int idx = text.indexOf(mnemonic);
			if (idx != -1) {
				text = text.substring(0, idx) + '&' + text.substring(idx);
			}
		}
		String iconUri = MenuHelper.getIconUrl(element, IWorkbenchRegistryConstants.ATT_ICON);
		final String cmdId = MenuHelper.getActionSetCommandId(element);

		MCommand cmd = ContributionsAnalyzer.getCommandById(app, cmdId);
		if (cmd == null) {
			ECommandService commandService = app.getContext().get(ECommandService.class);
			Command command = commandService.getCommand(cmdId);
			if (command == null) {
				ICommandService ics = app.getContext().get(ICommandService.class);
				command = commandService.defineCommand(cmdId, text, null, ics.getCategory(null),
						null);
			}
			cmd = CommandsFactoryImpl.eINSTANCE.createCommand();
			cmd.setCommandName(text);
			cmd.setElementId(cmdId);
			app.getCommands().add(cmd);
		}

		String style = element.getAttribute(IWorkbenchRegistryConstants.ATT_STYLE);
		String pulldown = element.getAttribute("pulldown"); //$NON-NLS-1$
		if (IWorkbenchRegistryConstants.STYLE_PULLDOWN.equals(style)
				|| (pulldown != null && pulldown.equals("true"))) { //$NON-NLS-1$
			MRenderedMenu menu = MenuFactoryImpl.eINSTANCE.createRenderedMenu();
			menu.setElementId(id);
			menu.setLabel(text);
			if (iconUri != null) {
				menu.setIconURI(iconUri);
			}
			ECommandService cs = app.getContext().get(ECommandService.class);
			final ParameterizedCommand parmCmd = cs.createCommand(cmdId, null);
			menu.setContributionManager(new IMenuCreator() {
				private ActionDelegateHandlerProxy handlerProxy;

				private ActionDelegateHandlerProxy getProxy() {
					if (handlerProxy == null) {
						handlerProxy = new ActionDelegateHandlerProxy(element,
								IWorkbenchRegistryConstants.ATT_CLASS, id, parmCmd, PlatformUI
										.getWorkbench().getActiveWorkbenchWindow(), null, null,
								null);
					}
					return handlerProxy;
				}

				private IWorkbenchWindowPulldownDelegate getDelegate() {
					getProxy();
					if (handlerProxy == null) {
						return null;
					}
					if (handlerProxy.getDelegate() == null) {
						handlerProxy.loadDelegate();
					}
					return (IWorkbenchWindowPulldownDelegate) handlerProxy.getDelegate();
				}

				public Menu getMenu(Menu parent) {
					IWorkbenchWindowPulldownDelegate2 delegate = (IWorkbenchWindowPulldownDelegate2) getDelegate();
					if (delegate == null) {
						return null;
					}
					return delegate.getMenu(parent);
				}

				public Menu getMenu(Control parent) {
					return getDelegate() == null ? null : getDelegate().getMenu(parent);
				}

				public void dispose() {
					if (handlerProxy != null) {
						handlerProxy.dispose();
						handlerProxy = null;
					}
				}
			});
			return menu;
		}

		ItemType type = ItemType.PUSH;
		if (IWorkbenchRegistryConstants.STYLE_TOGGLE.equals(style)) {
			type = ItemType.CHECK;
		} else if (IWorkbenchRegistryConstants.STYLE_RADIO.equals(style)) {
			type = ItemType.RADIO;
		}
		MHandledMenuItem item = MenuFactoryImpl.eINSTANCE.createHandledMenuItem();
		item.setElementId(id);
		item.setLabel(text);
		item.setType(type);
		item.setCommand(cmd);
		if (iconUri != null) {
			item.setIconURI(iconUri);
		}
		return item;
	}

	public static String getDescription(IConfigurationElement configElement) {
		return configElement.getAttribute(IWorkbenchRegistryConstants.TAG_DESCRIPTION);
	}

	public static MToolBarElement createLegacyToolBarActionAdditions(MApplication app,
			final IConfigurationElement element) {
		String cmdId = MenuHelper.getActionSetCommandId(element);
		final String id = MenuHelper.getId(element);
		String text = MenuHelper.getLabel(element);
		String mnemonic = MenuHelper.getMnemonic(element);
		if (text != null && mnemonic != null) {
			int idx = text.indexOf(mnemonic);
			if (idx != -1) {
				text = text.substring(0, idx) + '&' + text.substring(idx);
			}
		}
		String iconUri = MenuHelper.getIconUrl(element, IWorkbenchRegistryConstants.ATT_ICON);
		MCommand cmd = ContributionsAnalyzer.getCommandById(app, cmdId);
		if (cmd == null) {
			ECommandService commandService = app.getContext().get(ECommandService.class);
			Command command = commandService.getCommand(cmdId);
			if (command == null) {
				ICommandService ics = app.getContext().get(
						ICommandService.class);
				command = commandService.defineCommand(cmdId, text, null, ics.getCategory(null),
						null);
			}
			cmd = CommandsFactoryImpl.eINSTANCE.createCommand();
			cmd.setCommandName(text);
			cmd.setElementId(cmdId);
			app.getCommands().add(cmd);
		}
		final MHandledToolItem item = MenuFactoryImpl.eINSTANCE.createHandledToolItem();

		String style = element.getAttribute(IWorkbenchRegistryConstants.ATT_STYLE);
		if (style == null || style.length() == 0) {
			item.setType(ItemType.PUSH);
		} else if (IWorkbenchRegistryConstants.STYLE_TOGGLE.equals(style)) {
			item.setType(ItemType.CHECK);
		} else if (IWorkbenchRegistryConstants.STYLE_RADIO.equals(style)) {
			item.setType(ItemType.RADIO);
		} else if (IWorkbenchRegistryConstants.STYLE_PULLDOWN.equals(style)) {
			MRenderedMenu menu = MenuFactoryImpl.eINSTANCE.createRenderedMenu();
			ECommandService cs = app.getContext().get(ECommandService.class);
			final ParameterizedCommand parmCmd = cs.createCommand(cmdId, null);
			menu.setContributionManager(new IMenuCreator() {
				private ActionDelegateHandlerProxy handlerProxy;

				private ActionDelegateHandlerProxy getProxy() {
					if (handlerProxy == null) {
						handlerProxy = new ActionDelegateHandlerProxy(element,
								IWorkbenchRegistryConstants.ATT_CLASS, id, parmCmd, PlatformUI
										.getWorkbench().getActiveWorkbenchWindow(), null, null,
								null);
					}
					return handlerProxy;
				}

				private IWorkbenchWindowPulldownDelegate getDelegate() {
					getProxy();
					if (handlerProxy == null) {
						return null;
					}
					if (handlerProxy.getDelegate() == null) {
						handlerProxy.loadDelegate();
					}
					return (IWorkbenchWindowPulldownDelegate) handlerProxy.getDelegate();
				}

				public Menu getMenu(Menu parent) {
					IWorkbenchWindowPulldownDelegate2 delegate = (IWorkbenchWindowPulldownDelegate2) getDelegate();
					if (delegate == null) {
						return null;
					}
					return delegate.getMenu(parent);
				}

				public Menu getMenu(Control parent) {
					return getDelegate() == null ? null : getDelegate().getMenu(parent);
				}

				public void dispose() {
					if (handlerProxy != null) {
						handlerProxy.dispose();
						handlerProxy = null;
					}
				}
			});
			item.setMenu(menu);
		} else {
			item.setType(ItemType.PUSH);
		}
		
		item.setElementId(id);
		item.setCommand(cmd);
		if (iconUri == null) {
			item.setLabel(text);
		} else {
			item.setIconURI(iconUri);
		}
		return item;
	}
}
