import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;
import java.util.*;

public class PixelFlutClient {
    private static final String SERVER_ADDRESS = "pixelflut.3s.tu-berlin.de";
    private static final int SERVER_PORT = 60042;
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;
    private static final int THREAD_COUNT = 10;
    private static final int LINE_WIDTH = 100;
    private final HashMap<String, String> BACKUP_COLORS = new HashMap<>();

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Random random = new Random();

    public static void main(String[] args) {
        PixelFlutClient client = new PixelFlutClient();
        client.start();
    }

    public void start() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

//            sendCommand("HELP");
//            readServerResponse();


            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("Wählen Sie eine Aktion:");
                System.out.println("1 - Bildschirmgröße anzeigen");
                System.out.println("2 - Rechteck zeichnen");
                System.out.println("3 - Farbverlauf");
                System.out.println("4 - Rechteck springen lassen"); //teilweise funktioniert
                System.out.println("5 - Bildschirm fluten");
                System.out.println("6 - Linie zeichnen"); // nicht funktioniert
                System.out.println("0 - Beenden");

                int choice = scanner.nextInt();
                switch (choice) {
                    case 1:
                        printScreenSize();
                        break;
                    case 2:
                        drawRectangle(200, 200, 300, 300, getRandomColor());
                        break;
                    case 3:
                        drawGradientRectangle(200, 200, 300, 300);
                        break;
                    case 4:
                        animateJumpingRectangle(300, 300, 1000);
                        break;
                    case 5:
                        floodScreen(getRandomColor());
                        break;
                    case 6:
                        startMovingLine();
                        break;
                    case 0:
                        closeConnection();
                        return;
                    default:
                        System.out.println("Ungültige Auswahl.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendCommand(String command) {
        out.println(command);
    }

    private void readServerResponse() {
        try {
            String response;
            while ((response = in.readLine()) != null) {
                System.out.println(response);
                if (response.isEmpty()) break;
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Lesen der Serverantwort: " + e.getMessage());
        }
    }

    private void printScreenSize() {
        System.out.println("Bildschirmgröße: " + SCREEN_WIDTH + "x" + SCREEN_HEIGHT);
    }

    private void drawRectangle(int x, int y, int width, int height, String color) {
        for (int i = x; i < x + width; i++) {
            for (int j = y; j < y + height; j++) {
                sendCommand("PX " + i + " " + j + " " + color);
            }
        }
    }

    private void drawGradientRectangle(int x, int y, int width, int height) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                String color = String.format("%06x", (i * 255 / width) << 16 | (j * 255 / height) << 8);
                sendCommand("PX " + (x + i) + " " + (y + j) + " " + color);
            }
        }
    }

    private void animateJumpingRectangle(int width, int height, int interval) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 10_000;

        while (System.currentTimeMillis() < endTime) {
            int x = random.nextInt(SCREEN_WIDTH - width);
            int y = random.nextInt(SCREEN_HEIGHT - height);


//            String[][] originalColors = new String[width][height];
//            for (int dx = 0; dx < width; dx++) {
//                for (int dy = 0; dy < height; dy++) {
//                    originalColors[dx][dy] = getPixelColor(x + dx, y + dy);
//                }
//            }

            String color = getRandomColor();

            drawRectangle(x, y, width, height, color);

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Animation interrupted.");
                return;
            }

//            for (int dx = 0; dx < width; dx++) {
//                for (int dy = 0; dy < height; dy++) {
//                    sendCommand("PX " + (x + dx) + " " + (y + dy) + " " + originalColors[dx][dy]);
//                }
//            }
        }
    }

    private void floodScreen(String color) {
        for (int i = 0; i < SCREEN_WIDTH; i++) {
            for (int j = 0; j < SCREEN_HEIGHT; j++) {
                sendCommand("PX " + i + " " + j + " " + color);
            }
        }
    }

    private String getRandomColor() {
        return String.format("%06x", random.nextInt(0xFFFFFF));
    }

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
            if (out != null) out.close();
            if (in != null) in.close();
            System.out.println("Verbindung geschlossen.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getPixelColor(int x, int y) {
        try {
            sendCommand("PX " + x + " " + y);
            String response = in.readLine();

            String[] parts = response.split(" ");
            if (parts.length < 3) {
                System.err.println("Ungültige Server-Antwort: " + response);
                return "000000";
            }

            int color = Integer.parseInt(parts[3], 16);
            return String.format("%06x", color);

        } catch (IOException e) {
            System.err.println("Fehler beim Lesen der Pixel-Farbe: " + e.getMessage());
            return "000000";
        }
    }

    public void startMovingLine() {
        new Thread(new DrawLineTask()).start();
    }

    private class DrawLineTask implements Runnable {
        private int x = 0;
        private String drawColor = getRandomColor();

        @Override
        public void run() {
            while (true) {
                BACKUP_COLORS.clear();

                for (int dx = 0; dx < LINE_WIDTH; dx++) {
                    int currentX = (x + dx) % SCREEN_WIDTH;
                    for (int y = 0; y < SCREEN_HEIGHT; y++) {
                        String key = currentX + "," + y;
                        BACKUP_COLORS.put(key, getPixelColor(currentX, y));
                        sendCommand("PX " + currentX + " " + y + " " + drawColor);
                    }
                }

                for (int i = 0; i < THREAD_COUNT; i++) {
                    new Thread(new RestoreTask(i)).start();
                }

                x = (x + 1) % SCREEN_WIDTH;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private class RestoreTask implements Runnable {
        private final int threadIndex;

        public RestoreTask(int threadIndex) {
            this.threadIndex = threadIndex;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(500 + threadIndex * 50);
                synchronized (BACKUP_COLORS) {
                    for (String key : BACKUP_COLORS.keySet()) {
                        String[] parts = key.split(",");
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        sendCommand("PX " + x + " " + y + " " + BACKUP_COLORS.get(key));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}