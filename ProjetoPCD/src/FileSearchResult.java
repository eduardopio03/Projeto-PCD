
import java.io.Serializable;

public class FileSearchResult implements Serializable {

    private WordSearchMessage searchMessage;
    private int fileSize;
    private String fileName;
    private int originPort;
    private String hostName;

    public FileSearchResult(WordSearchMessage searchMessage, int fileSize, String fileName, int originPort, String hostName) {
        this.searchMessage = searchMessage;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.originPort = originPort;
        this.hostName = hostName;
    }

    public String getFileName() {
        return fileName;
    }

    public int getFileSize() {
        return fileSize;
    }

    public String getHostName() {
        return hostName;
    }

    public int getOriginPort() {
        return originPort;
    }

    public WordSearchMessage getSearchMessage() {
        return searchMessage;
    }

}
