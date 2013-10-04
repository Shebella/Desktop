package org.itri.ccma.safebox;

public interface ApplicationInstanceListener {
    public void newInstanceCreated();
    public void noticeMessage(String msg);
}
