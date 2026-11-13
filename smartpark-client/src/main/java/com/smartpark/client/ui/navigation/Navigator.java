package com.smartpark.client.ui.navigation;

import com.smartpark.client.app.AppContext;
import com.smartpark.client.session.UserSession;
import com.smartpark.client.ui.login.LoginController;
import com.smartpark.client.ui.shell.MainLayoutController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Reusable navigation for the whole application (call it on the JavaFX thread).
 * <ul>
 *     <li>{@link #showLogin(String)} shows the login screen (initially, after logout, or when the session expires);</li>
 *     <li>{@link #showMainLayout(UserSession)} shows the sidebar shell and the role's home screen;</li>
 *     <li>{@link #navigate(View)} swaps the content area, <b>after checking {@link AccessPolicy}</b>.
 *         A screen the role may not open is never loaded; the access-denied screen is shown instead.</li>
 * </ul>
 * Controllers are created by a controller factory that passes the {@link AppContext} to any
 * controller with an {@code (AppContext)} constructor, a simple form of dependency injection.
 */
public final class Navigator {

    private static final Logger LOG = Logger.getLogger(Navigator.class.getName());

    private final Stage stage;
    private final AppContext context;
    private final Scene scene;
    private MainLayoutController shell;
    private ViewController currentController;
    private View currentView;

    public Navigator(Stage stage, AppContext context, Scene scene) {
        this.stage = stage;
        this.context = context;
        this.scene = scene;
    }

    public void showLogin(String message) {
        leaveCurrentView();
        shell = null;
        Loaded<LoginController> login = load("/fxml/login.fxml");
        login.controller().showMessage(message);
        scene.setRoot(login.root());
        stage.setTitle("SmartPark - Sign in");
    }

    public void showMainLayout(UserSession session) {
        Loaded<MainLayoutController> layout = load("/fxml/main-layout.fxml");
        shell = layout.controller();
        shell.init(session, AccessPolicy.sidebarViews(session.role()), this::navigate);
        scene.setRoot(layout.root());
        stage.setTitle("SmartPark - " + session.role().displayName() + " - " + session.terminal());
        navigate(AccessPolicy.homeView(session.role()));
    }

    public void navigate(View requested) {
        UserSession session = context.session().current().orElse(null);
        if (session == null || shell == null) {
            showLogin("Please sign in.");
            return;
        }
        View view = requested;
        if (!AccessPolicy.canAccess(session.role(), requested)) {
            LOG.warning(() -> session.username() + " (" + session.role() + ") tried to open " + requested);
            view = View.ACCESS_DENIED;
        }
        leaveCurrentView();
        Loaded<Object> loaded = load(view.fxml());
        shell.showContent(view, loaded.root());
        currentView = view;
        currentController = loaded.controller() instanceof ViewController vc ? vc : null;
        if (currentController != null) {
            currentController.onShow();
        }
    }

    public View currentView() {
        return currentView;
    }

    private void leaveCurrentView() {
        if (currentController != null) {
            currentController.onHide();
        }
        currentController = null;
        currentView = null;
    }

    // ----------------------------------------------------------------- loading

    private record Loaded<C>(Parent root, C controller) {
    }

    private <C> Loaded<C> load(String fxml) {
        URL url = Objects.requireNonNull(getClass().getResource(fxml), "Missing FXML " + fxml);
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(this::createController);
        try {
            Parent root = loader.load();
            return new Loaded<>(root, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + fxml, e);
        }
    }

    private Object createController(Class<?> type) {
        try {
            for (Constructor<?> c : type.getConstructors()) {
                if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == AppContext.class) {
                    return c.newInstance(context);
                }
            }
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create controller " + type.getName(), e);
        }
    }
}
