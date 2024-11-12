package application;

import controllers.MainController;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class DBest {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException | UnsupportedLookAndFeelException exception) {
            exception.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            MainController mainController = new MainController();
            mainController.setVisible(true);
        });
    }
}
