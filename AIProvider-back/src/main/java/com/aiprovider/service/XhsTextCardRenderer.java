package com.aiprovider.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Component
public class XhsTextCardRenderer {
    private final Path directory;
    public XhsTextCardRenderer(@Value("${xiaohongshu.card-directory:/opt/aiprovider/xhs-cards}") String directory){this.directory=Paths.get(directory).toAbsolutePath().normalize();}
    public Path render(long publicationId,String title,String body){
        com.aiprovider.logging.BusinessOperationLogger.start("service.XhsTextCardRenderer.render", new String[] { "publicationId", "title", "body" }, new Object[] { publicationId, title, body });
        try{Files.createDirectories(directory);Path file=directory.resolve("publication-"+publicationId+".png").normalize();if(!file.startsWith(directory))throw new SecurityException("文字卡输出路径不合法");BufferedImage image=new BufferedImage(1080,1440,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(247,244,238));g.fillRect(0,0,1080,1440);g.setColor(new Color(30,34,40));g.setFont(new Font("SansSerif",Font.BOLD,64));int y=150;for(String line:wrap(title,14,4)){g.drawString(line,90,y);y+=86;}g.setColor(new Color(209,70,84));g.fillRoundRect(90,y+10,130,10,5,5);y+=100;g.setColor(new Color(52,57,65));g.setFont(new Font("SansSerif",Font.PLAIN,38));for(String line:wrap(body,24,18)){g.drawString(line,90,y);y+=58;}g.setFont(new Font("SansSerif",Font.PLAIN,26));g.setColor(new Color(125,127,132));g.drawString("AI 内容观察",90,1350);}finally{g.dispose();}ImageIO.write(image,"png",file.toFile());return com.aiprovider.logging.BusinessOperationLogger.success("service.XhsTextCardRenderer.render", file);}catch(IOException e){throw new XiaohongshuAutomationException("生成小红书文字卡失败",e);}}
    private List<String> wrap(String value,int chars,int maxLines){String text=value==null?"":value.replaceAll("\\s+"," ").trim();List<String> lines=new ArrayList<>();for(int i=0;i<text.length()&&lines.size()<maxLines;i+=chars)lines.add(text.substring(i,Math.min(text.length(),i+chars)));return lines;}
}
