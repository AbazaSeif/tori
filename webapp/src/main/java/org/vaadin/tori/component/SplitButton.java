package org.vaadin.tori.component;

import org.vaadin.hene.popupbutton.PopupButton;

import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;

@SuppressWarnings("serial")
public class SplitButton extends CustomComponent {
    public interface PopupVisibilityEvent {
        boolean isPopupVisible();

        SplitButton getSplitButton();
    }

    public interface PopupVisibilityListener {
        void splitButtonPopupVisibilityChange(final PopupVisibilityEvent event);
    }

    public interface ClickListener {
        void splitButtonClick();
    }

    private final Button button;
    private final PopupButton popupButton;

    public SplitButton(final String caption) {
        button = new Button(caption);
        popupButton = new PopupButton("v");

        final HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.addComponent(button);
        horizontalLayout.addComponent(popupButton);
        setCompositionRoot(horizontalLayout);
    }

    public void addClickListener(final ClickListener listener) {
        button.addListener(new Button.ClickListener() {
            @Override
            public void buttonClick(final ClickEvent event) {
                listener.splitButtonClick();
            }
        });
    }

    public void addPopupVisibilityListener(
            final PopupVisibilityListener popupVisibilityListener) {
        popupButton
                .addPopupVisibilityListener(new PopupButton.PopupVisibilityListener() {
                    @Override
                    public void popupVisibilityChange(
                            final PopupButton.PopupVisibilityEvent event) {

                        final PopupVisibilityEvent event2 = new PopupVisibilityEvent() {
                            @Override
                            public boolean isPopupVisible() {
                                return popupButton.isPopupVisible();
                            }

                            @Override
                            public SplitButton getSplitButton() {
                                return SplitButton.this;
                            }
                        };

                        popupVisibilityListener
                                .splitButtonPopupVisibilityChange(event2);
                    }
                });
    }

    public void setComponent(final Component component) {
        popupButton.setComponent(component);
    }

    public void setPopupVisible(final boolean visible) {
        popupButton.setPopupVisible(visible);
    }
}