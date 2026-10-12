package net.linlan.dsh.domain.tool.web.computer.adapter.port;

public interface IComputerUsePort {

    byte[] screenshot();

    void mouseClick(int x, int y);

    void mouseMove(int x, int y);

    void keyType(String text);

    void keyPress(String key);
}
