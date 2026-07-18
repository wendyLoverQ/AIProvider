package com.aiprovider.model.dto;

public class CommentReplyRequestDTO {
    private String postTitle; private String postBody; private String commentText; private String commenterName; private String extraInstruction;
    public String getPostTitle(){return postTitle;} public void setPostTitle(String v){postTitle=v;}
    public String getPostBody(){return postBody;} public void setPostBody(String v){postBody=v;}
    public String getCommentText(){return commentText;} public void setCommentText(String v){commentText=v;}
    public String getCommenterName(){return commenterName;} public void setCommenterName(String v){commenterName=v;}
    public String getExtraInstruction(){return extraInstruction;} public void setExtraInstruction(String v){extraInstruction=v;}
}
