package com.wenjunhuang.codeepiphany.toolwindows.sidebar.notes;

import com.wenjunhuang.codeepiphany.presentation.DomainPresentation;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import com.wenjunhuang.codeepiphany.domain.CodeDojo;
import com.wenjunhuang.codeepiphany.presentation.ui.TagLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class NoteListView {

    private JBLabel myChallengeName;
    private JComponent mySolutionPane;
    private JPanel myRootPanel;
    private TagLabel myDifficulty;
    private TagLabel myCodeDojo;
    private TreeTableView myTreeTable;

    private NotesListPresenter myPresenter;

    public NoteListView(NotesListPresenter presenter) {
        myPresenter = presenter;
        $$$setupUI$$$();
    }

    private void createUIComponents() {
        myDifficulty = new TagLabel();
        myCodeDojo = new TagLabel();
        myTreeTable = new TreeTableView(myPresenter.getTreeModel());
        myTreeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myTreeTable.setColumnSelectionAllowed(false);
        myTreeTable.getTree().setRootVisible(false);
        myTreeTable.getTree().setCellRenderer(myPresenter.getTreeRenderer());
        mySolutionPane =
                ToolbarDecorator.createDecorator(myTreeTable)
                        .addExtraAction(new AnAction("Modify Name", null, AllIcons.Actions.EditSource) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                var selected = myTreeTable.getSelection();
                                if (selected != null && !selected.isEmpty()) {
                                    if (selected.get(0) instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof NoteEntry.NoteNode solution) {
                                        if (!solution.isDefault()) {
                                            var result = Messages.showInputDialog(myPresenter.getProject(), "Modify solution name", "Modify Solution Name", null, solution.getTitle(), new InputValidator() {
                                                @Override
                                                public boolean checkInput(@NlsSafe String inputString) {
                                                    return myPresenter.isSolutionTitleAvailable(inputString);
                                                }

                                                @Override
                                                public boolean canClose(@NlsSafe String inputString) {
                                                    return true;
                                                }
                                            }, null);
                                            if (result != null) {
                                                myPresenter.modifySolutionTitle(solution.getSolutionId(), node, result);
                                            }
                                        }

                                    }
                                }
                            }

                            @Override
                            public void update(@NotNull AnActionEvent e) {
                                var selected = myTreeTable.getSelection();
                                if (selected != null && !selected.isEmpty()) {
                                    e.getPresentation().setEnabled(selected.get(0) instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof NoteEntry.NoteNode);
                                } else {
                                    e.getPresentation().setEnabled(false);
                                }
                            }

                            @Override
                            public @NotNull ActionUpdateThread getActionUpdateThread() {
                                return ActionUpdateThread.EDT;
                            }
                        })
                        .addExtraAction(new AnAction("Modify Readme", null, AllIcons.Actions.EditScheme) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                var selected = myTreeTable.getSelection();
                                if (selected != null && !selected.isEmpty()) {
                                    if (selected.get(0) instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof NoteEntry.NoteNode solution) {
                                        myPresenter.openSolutionRemarkEditor(solution.getSolutionId());
                                    }
                                }
                            }

                            @Override
                            public void update(@NotNull AnActionEvent e) {
                                var selected = myTreeTable.getSelection();
                                if (selected != null && !selected.isEmpty()) {
                                    e.getPresentation().setEnabled(selected.get(0) instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof NoteEntry.NoteNode);
                                } else {
                                    e.getPresentation().setEnabled(false);
                                }
                            }

                            @Override
                            public @NotNull ActionUpdateThread getActionUpdateThread() {
                                return ActionUpdateThread.EDT;
                            }
                        })
                        .addExtraAction(new AnAction("Add Solution", null, AllIcons.General.Add) {
                            @Override
                            public void actionPerformed(@NotNull AnActionEvent e) {
                                var result = Messages.showInputDialog(myPresenter.getProject(), "Add solution", "Add Solution", null, "", new InputValidator() {
                                    @Override
                                    public boolean checkInput(@NlsSafe String inputString) {
                                        return myPresenter.isSolutionTitleAvailable(inputString);
                                    }

                                    @Override
                                    public boolean canClose(@NlsSafe String inputString) {
                                        return true;
                                    }
                                }, null);
                                if (result != null) {
                                    myPresenter.addNewSolution(result);
                                }
                            }

                            @Override
                            public @NotNull ActionUpdateThread getActionUpdateThread() {
                                return ActionUpdateThread.EDT;
                            }
                        })
                        .addExtraAction(new AnAction("Refresh", null, AllIcons.Actions.Refresh) {
                            @Override
                            public void actionPerformed(AnActionEvent e) {
                                myPresenter.requery();
                            }

                            @Override
                            public @NotNull ActionUpdateThread getActionUpdateThread() {
                                return ActionUpdateThread.EDT;
                            }
                        })
                        .setScrollPaneBorder(JBUI.Borders.empty())
                        .setPanelBorder(JBUI.Borders.empty())
                        .setToolbarBorder(JBUI.Borders.empty())
                        .createPanel();
    }

    public JComponent getComponent() {
        return myRootPanel;
    }

    public void setChallengeName(String name) {
        myChallengeName.setText(name);
    }

    public void setDifficulty(String difficulty) {
        myDifficulty.setText(difficulty);
    }

    public void setCodeDojo(CodeDojo codeDojo) {
        myCodeDojo.setText(DomainPresentation.getShow(codeDojo));
        myCodeDojo.setIcon(DomainPresentation.getIcon(codeDojo));
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        myRootPanel = new JPanel();
        myRootPanel.setLayout(new GridLayoutManager(2, 6, new Insets(0, 0, 0, 0), 5, -1));
        myRootPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        myRootPanel.add(mySolutionPane, new GridConstraints(1, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myRootPanel.add(myDifficulty, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myRootPanel.add(spacer1, new GridConstraints(0, 4, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        myRootPanel.add(myCodeDojo, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout(0, 0));
        myRootPanel.add(panel1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        myChallengeName = new JBLabel();
        myChallengeName.setText("");
        panel1.add(myChallengeName, BorderLayout.CENTER);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myRootPanel;
    }

}
