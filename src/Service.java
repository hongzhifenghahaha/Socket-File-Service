import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 该类提供了服务端的服务
 *
 * @author Sun Shuo
 * @version 1.2.0
 */
public class Service implements Runnable {
    // 设置文件传送端口，缓冲区大小
    private final int UDP_PORT = 2020;
    private final int MAX_BUFFER_SIZE = 1024;

    private String rootAbsolutelyPath;
    private String currentWorkingDirectory;

    private Socket socket;
    private DatagramSocket udpSocket;

    BufferedReader br;
    BufferedWriter bw;
    PrintWriter pw;

    /**
     * 构造函数，初始化socket、根目录路径和当前工作路径
     *
     * @param socket tcp socket
     * @param udpSocket udp socket
     * @param root   根目录的路径
     */
    public Service(Socket socket, DatagramSocket udpSocket, String root) throws IOException {
        this.socket = socket;
        this.udpSocket = udpSocket;

        this.rootAbsolutelyPath = new File(root).getAbsolutePath();
        this.currentWorkingDirectory = root;
    }

    /**
     * 初始化 IO 流
     */
    public void initStream() throws IOException {
        // 从tcp Socket中获取输入流，构造br，用于接收来自客户端的指令
        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // 从tcp Socket中获取输出流，构造pw，用于向客户端发送指令
        bw = new BufferedWriter(new OutputStreamWriter((socket.getOutputStream())));
        pw = new PrintWriter(bw, true);
    }

    /**
     * 处理来自客户端的指令
     */
	@Override
    public void run() {
        try {
            // 初始化 IO 流
            initStream();

            // 打印连接信息
            System.out.println("新的TCP连接，连接地址：" + socket.getInetAddress() + "：" + socket.getPort());
            pw.println(socket.getInetAddress() + ":" + socket.getPort() + ">连接成功");

            // 逐行读入并处理来自客户端的指令
            String cmd = null, commandType = null, commandParam = null;
            // 通过br读取来自客户端的指令
            while ((cmd = br.readLine()) != null) {
                // 打印来自客户端的指令到屏幕上
                System.out.println("执行指令: " + cmd);

                // 用空格分割参数
                String[] commandList = cmd.trim().split("\\s+");
                commandType = commandList[0];

                // 处理不带路径参数的 cd.. 指令，返回上一级目录
                if (commandType.equals("cd..")){
                    cd("..");
                }
                // 处理ls指令，并通过pw将结果返回给客户端
                else if (commandType.equals("ls")) {
                    if (commandList.length > 1) {
                        pw.println("参数过多，请重新输入！");
                    }
                    else {
                        ls();
                    }
                }
                // 处理带路径参数的cd指令，并通过pw将结果返回给客户端
                else if (commandType.equals("cd")) {
                    if (commandList.length == 2) {
                        commandParam = commandList[1];
                        cd(commandParam);
                    }
                    else if (commandList.length < 2){
                        pw.println("参数过少，请重新输入！");
                    } else {
                        pw.println("参数过多，请重新输入！");
                    }
                }
                // 处理get指令，并通过pw将结果返回给客户端
                else if (commandType.equals("get")) {
                    if (commandList.length == 2) {
                        commandParam = commandList[1];
                        get(commandParam);
                    }
                    else if (commandList.length > 2) {
                        pw.println("参数过多，请重新输入！");
                    }
                    else {
                        pw.println("参数过少，请重新输入！");
                    }
                }
                // 处理bye指令，断开与客户端的连接
                else if (commandType.equals("bye") && commandList.length == 1) {
                    pw.println("连接中断");
                    System.out.println("端口 " + socket.getPort() + " 断开连接");
                    break;
                }
                // 其他指令直接无视
                else {
                    pw.println("unknown cmd");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭socket
            if (null != socket) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 处理ls指令 -- 列出当前工作目录下的所有文件
     */
    public void ls() {
        File currentFiles;
        currentFiles = new File(currentWorkingDirectory);
        File[] fileList = currentFiles.listFiles();

        // 追加fileList中所有文件的信息到StringBuilder中
        StringBuilder sb = new StringBuilder();
        if (null != fileList) {
            for (File file : fileList) {
                String reply = "";
                // file是目录的话，则输出目录名，并计算目录的大小
                if (file.isDirectory()) {
                    reply = String.format("%-9s%-20s%-20s\n", "<dir>", file.getName(), "" + getDirectorySize(file));
                }
                // file是文件的话，则输出文件名，并输出文件的大小
                else if (file.isFile()) {
                    reply = String.format("%-9s%-20s%-20s\n", "<file>", file.getName(), "" + file.length());
                }
                sb.append(reply);
            }
            // 将结果响应给客户端
            pw.print(sb.toString());
            pw.flush();
        }
    }

    /**
     * 递归计算目录的空间大小
     *
     * @param directory 被用于计算空间大小的目录
     * @return 目录空间大小
     */
    public static long getDirectorySize(File directory) {
        long length = 0;
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            // file是文件的话，则直接加上空间大学
            if (file.isFile()) {
                length += file.length();
            }
            // file是目录的话，则递归计算该目录的大小并累加起来
            else {
                length += getDirectorySize(file);
            }
        }
        return length;
    }

    /**
     * 执行 cd 指令 -- 改变当前工作目录
     *
     * @param path 将变化到的目录
     */
    public void cd(String path) throws IOException {
        // 计算新目录的路径
        File file = new File(currentWorkingDirectory + File.separator + path);

        // file不存在或者file不是目录
        if (! (file.exists() && file.isDirectory())) {
            pw.println("unknown dir");
        }
        else {
            //将文件完整路径转变为字符串
            String newPath = file.toPath().toRealPath().toString();
            //如果该文件路径的开头不是根目录，说明该文件路径超出了根目录的范围，则提示用户没有权限到达根目录的上层目录。
            if (!newPath.startsWith(rootAbsolutelyPath)) {
                pw.println("权限错误: 你无法访问根目录的上级目录!");
            }
            // 否则就修改当前工作目录，并向客户端响应ok
            else {
                currentWorkingDirectory = newPath;
                String currentPath = newPath.substring(newPath.lastIndexOf(File.separator) + 1);
                pw.println(currentPath + " > OK");
            }
        }
    }

    /**
     * 执行 get 指令 -- 通过udp向客户端发送文件
     *
     * @param fileName 要get的文件名
     */
    public void get(String fileName) throws IOException {
        // 获取文件完整路径
        String filePath = currentWorkingDirectory + File.separator + fileName;
        File file = new File(filePath);

        if (!file.exists()) {
            pw.println("unknown file");
            return;
        }
        else {
            // 判断文件路径是否超出根目录的范围，超出则提示用户没有权限到达根目录的上层目录。
            String realPath = file.toPath().toRealPath().toString();
            if (!realPath.startsWith(rootAbsolutelyPath)) {
                pw.println("权限错误: 你无法访问根目录的上级目录!");
                return;
            }
            if (!file.isFile()) {
                pw.println("文件类型错误： 请输入合法文件名作为参数！");
                return;
            }
        }

        pw.println("文件正在传送：" + file.getName());

        // 从客户端接收一个小的datagramPacket来开启文件传输过程
        DatagramPacket dp = new DatagramPacket(new byte[MAX_BUFFER_SIZE], MAX_BUFFER_SIZE);
        udpSocket.receive(dp);

        String clientIP = new String(dp.getData(), dp.getOffset(), dp.getLength(), StandardCharsets.UTF_8);
        System.out.println("接收到来自客户端 " + clientIP + " 的packet，开始文件传输");

        // 向客户端发送文件
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))){

            byte[] buffer = new byte[MAX_BUFFER_SIZE];
            while (bis.read(buffer) > 0) {
                dp.setData(buffer);
                udpSocket.send(dp);
                Thread.sleep(10); // 限制文件传送速度，避免文件乱序
            }
            System.out.println("传输文件 " + fileName + " 完成");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
