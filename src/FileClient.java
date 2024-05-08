import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * 文件服务的客户端
 *
 * @author Sun Shuo
 * @version 1.2.0
 */
public class FileClient {
    // 定义tcp端口，udp端口、文件缓冲区的大小和服务端ip
    private final int TCP_PORT = 2021;
    private final int UDP_PORT = 2020;
    private final int MAX_BUFFER_SIZE = 1024;
    private String SERVER_IP;

    BufferedWriter bw;
    PrintWriter pw;
    BufferedReader br;
    Scanner in;

    // 定义tcp socket和udp socket
    Socket tcpSocket;
    DatagramSocket udpSocket;

    /**
     * 构造函数，初始化服务端ip，tcpSocket和udpSocket
     */
    public FileClient(String ip) throws UnknownHostException, IOException, InterruptedException {
        // 将参数中的ip赋值给SERVER_IP
        this.SERVER_IP = ip;

        boolean connection_success = false;
        int connection_count = 0;

        while (!connection_success){
            try {
                connection_count++;
                if (connection_count >= 10){
                    System.out.println("连接次数过多，终止程序！");
                    return;
                }

                // 创建tcpSocket并根据 SERVER_IP 和 TCP_PORT 连接到服务器的socket
                tcpSocket = new Socket();
                tcpSocket.connect(new InetSocketAddress(SERVER_IP, TCP_PORT));

                // 创建udpSocket，设置超时时间并根据 SERVER_IP 和 UDP_PORT 连接到服务器
                udpSocket = new DatagramSocket();
                udpSocket.setSoTimeout(1000);
                udpSocket.connect(new InetSocketAddress(SERVER_IP, UDP_PORT));

                connection_success = true;
            } catch (ConnectException ce){
                System.out.println("连接错误，3秒后尝试重新连接......");
                Thread.sleep(3000);
            }
        }
    }

    /**
     * main函数，运行文件服务客户端，并向服务端发送命令
     */
    public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
        // 判断参数中是否有服务器的ip
        if (!(args.length == 1)) {
            System.out.println("错误：请输入服务器ip作为参数");
        }
        else {
            // 调用构造函数，传入ip参数并调用send方法
            new FileClient(args[0]).send();
        }
    }

    /**
     * 初始化 IO streams
     */
    public void initStream() throws IOException {
        // 从tcpSocket中获取输出流，构造pw，用于向服务端发送指令
        // 字节： 二进制字节 01010， 字符： 中英文字符，标点符号
        bw = new BufferedWriter(new OutputStreamWriter(tcpSocket.getOutputStream()));
        pw = new PrintWriter(bw, true);

        // 从tcpSocket中获取输入流，构造br，用于接收来自服务端的指令
        br = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));

        // Scanner 从键盘读取用户指令
        in = new Scanner(System.in);
    }

    /**
     * 读取用户指令，向服务端发送指令，并处理来自服务端的响应
     */
    public void send() {
        try {
            // 初始化 IO 流
            initStream();

            // 打印连接信息
            String connectInfo = br.readLine();
            System.out.println(connectInfo);

            String cmd = null, reply = null, commandType = null;
            // 从键盘逐行读取指令，赋值给cmd
            while (null != (cmd = in.nextLine())) {
                // 通过pw将cmd发给服务端
                pw.println(cmd);

                // 用空格分割指令参数，逐个存入commandList
                String[] commandList = cmd.trim().split("\\s+");
                commandType = commandList[0];

                // 通过br读取来自服务端的响应
                while ((null != (reply = br.readLine())) && reply.length() > 0) {
                    // 打印服务端的响应到屏幕上
                    System.out.println(reply);
                    // 无响应则中断循环
                    if (!br.ready()) {
                        break;
                    }
                }

                // 若当前的指令为 bye，则终止客户端
                if (commandType.equals("bye") && commandList.length == 1) {
                    break;
                }

                // 若当前指令为 get，并且服务端的响应为“文件正在传送”，则打印“文件正在传送”
                else if (commandType.equals("get") && reply.startsWith("文件正在传送")) {
                    String fileName = reply.substring("文件正在传送：".length());
                    receiveFiles(fileName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭socket
            if (null != udpSocket) {
                try {
                    udpSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (null != tcpSocket) {
                try {
                    tcpSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * 从服务端读取文件
     *
     * @param fileName 想从服务端获取的文件的文件名
     */
    public void receiveFiles(String fileName) throws IOException {
        // 先发送一个小的 datagramPacket 来开启文件传输
        String clientIP = InetAddress.getLocalHost().getHostAddress();
        byte[] data = clientIP.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length);
        udpSocket.send(sendPacket);

        // 定义一次读入的数据量
        DatagramPacket receivedPacket = new DatagramPacket(new byte[MAX_BUFFER_SIZE], MAX_BUFFER_SIZE);
        // 开始准备接收文件
        try( FileOutputStream fos = new FileOutputStream(new File(fileName))) {

            // 从服务端接收字节并输出到文件中
            try {
                while (true) {
                    udpSocket.receive(receivedPacket);
                    byte[] buffer = receivedPacket.getData();
                    fos.write(buffer);
                    fos.flush();
                }
            } catch (SocketTimeoutException e) {
            } finally {
                System.out.println("文件传输结束");
            }
        } catch (FileNotFoundException e) {
            System.out.println("文件: '" + fileName + "' 无法创建在本地，请检查文件名是否正确！");
        }

    }
}
