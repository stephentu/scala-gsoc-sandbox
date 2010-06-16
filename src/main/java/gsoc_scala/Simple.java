// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: src/main/protobuf/simple.proto

package gsoc_scala;

public final class Simple {
  private Simple() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  public static final class ProtobufSimpleMessage extends
      com.google.protobuf.GeneratedMessage {
    // Use ProtobufSimpleMessage.newBuilder() to construct.
    private ProtobufSimpleMessage() {
      initFields();
    }
    private ProtobufSimpleMessage(boolean noInit) {}
    
    private static final ProtobufSimpleMessage defaultInstance;
    public static ProtobufSimpleMessage getDefaultInstance() {
      return defaultInstance;
    }
    
    public ProtobufSimpleMessage getDefaultInstanceForType() {
      return defaultInstance;
    }
    
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return gsoc_scala.Simple.internal_static_gsoc_scala_ProtobufSimpleMessage_descriptor;
    }
    
    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return gsoc_scala.Simple.internal_static_gsoc_scala_ProtobufSimpleMessage_fieldAccessorTable;
    }
    
    // required int32 id = 1;
    public static final int ID_FIELD_NUMBER = 1;
    private boolean hasId;
    private int id_ = 0;
    public boolean hasId() { return hasId; }
    public int getId() { return id_; }
    
    // required string text = 2;
    public static final int TEXT_FIELD_NUMBER = 2;
    private boolean hasText;
    private java.lang.String text_ = "";
    public boolean hasText() { return hasText; }
    public java.lang.String getText() { return text_; }
    
    private void initFields() {
    }
    public final boolean isInitialized() {
      if (!hasId) return false;
      if (!hasText) return false;
      return true;
    }
    
    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (hasId()) {
        output.writeInt32(1, getId());
      }
      if (hasText()) {
        output.writeString(2, getText());
      }
      getUnknownFields().writeTo(output);
    }
    
    private int memoizedSerializedSize = -1;
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;
    
      size = 0;
      if (hasId()) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt32Size(1, getId());
      }
      if (hasText()) {
        size += com.google.protobuf.CodedOutputStream
          .computeStringSize(2, getText());
      }
      size += getUnknownFields().getSerializedSize();
      memoizedSerializedSize = size;
      return size;
    }
    
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data).buildParsed();
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return newBuilder().mergeFrom(data, extensionRegistry)
               .buildParsed();
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input, extensionRegistry)) {
        return builder.buildParsed();
      } else {
        return null;
      }
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input).buildParsed();
    }
    public static gsoc_scala.Simple.ProtobufSimpleMessage parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return newBuilder().mergeFrom(input, extensionRegistry)
               .buildParsed();
    }
    
    public static Builder newBuilder() { return Builder.create(); }
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(gsoc_scala.Simple.ProtobufSimpleMessage prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() { return newBuilder(this); }
    
    public static final class Builder extends
        com.google.protobuf.GeneratedMessage.Builder<Builder> {
      private gsoc_scala.Simple.ProtobufSimpleMessage result;
      
      // Construct using gsoc_scala.Simple.ProtobufSimpleMessage.newBuilder()
      private Builder() {}
      
      private static Builder create() {
        Builder builder = new Builder();
        builder.result = new gsoc_scala.Simple.ProtobufSimpleMessage();
        return builder;
      }
      
      protected gsoc_scala.Simple.ProtobufSimpleMessage internalGetResult() {
        return result;
      }
      
      public Builder clear() {
        if (result == null) {
          throw new IllegalStateException(
            "Cannot call clear() after build().");
        }
        result = new gsoc_scala.Simple.ProtobufSimpleMessage();
        return this;
      }
      
      public Builder clone() {
        return create().mergeFrom(result);
      }
      
      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return gsoc_scala.Simple.ProtobufSimpleMessage.getDescriptor();
      }
      
      public gsoc_scala.Simple.ProtobufSimpleMessage getDefaultInstanceForType() {
        return gsoc_scala.Simple.ProtobufSimpleMessage.getDefaultInstance();
      }
      
      public boolean isInitialized() {
        return result.isInitialized();
      }
      public gsoc_scala.Simple.ProtobufSimpleMessage build() {
        if (result != null && !isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return buildPartial();
      }
      
      private gsoc_scala.Simple.ProtobufSimpleMessage buildParsed()
          throws com.google.protobuf.InvalidProtocolBufferException {
        if (!isInitialized()) {
          throw newUninitializedMessageException(
            result).asInvalidProtocolBufferException();
        }
        return buildPartial();
      }
      
      public gsoc_scala.Simple.ProtobufSimpleMessage buildPartial() {
        if (result == null) {
          throw new IllegalStateException(
            "build() has already been called on this Builder.");
        }
        gsoc_scala.Simple.ProtobufSimpleMessage returnMe = result;
        result = null;
        return returnMe;
      }
      
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof gsoc_scala.Simple.ProtobufSimpleMessage) {
          return mergeFrom((gsoc_scala.Simple.ProtobufSimpleMessage)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }
      
      public Builder mergeFrom(gsoc_scala.Simple.ProtobufSimpleMessage other) {
        if (other == gsoc_scala.Simple.ProtobufSimpleMessage.getDefaultInstance()) return this;
        if (other.hasId()) {
          setId(other.getId());
        }
        if (other.hasText()) {
          setText(other.getText());
        }
        this.mergeUnknownFields(other.getUnknownFields());
        return this;
      }
      
      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder(
            this.getUnknownFields());
        while (true) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              this.setUnknownFields(unknownFields.build());
              return this;
            default: {
              if (!parseUnknownField(input, unknownFields,
                                     extensionRegistry, tag)) {
                this.setUnknownFields(unknownFields.build());
                return this;
              }
              break;
            }
            case 8: {
              setId(input.readInt32());
              break;
            }
            case 18: {
              setText(input.readString());
              break;
            }
          }
        }
      }
      
      
      // required int32 id = 1;
      public boolean hasId() {
        return result.hasId();
      }
      public int getId() {
        return result.getId();
      }
      public Builder setId(int value) {
        result.hasId = true;
        result.id_ = value;
        return this;
      }
      public Builder clearId() {
        result.hasId = false;
        result.id_ = 0;
        return this;
      }
      
      // required string text = 2;
      public boolean hasText() {
        return result.hasText();
      }
      public java.lang.String getText() {
        return result.getText();
      }
      public Builder setText(java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  result.hasText = true;
        result.text_ = value;
        return this;
      }
      public Builder clearText() {
        result.hasText = false;
        result.text_ = getDefaultInstance().getText();
        return this;
      }
      
      // @@protoc_insertion_point(builder_scope:gsoc_scala.ProtobufSimpleMessage)
    }
    
    static {
      defaultInstance = new ProtobufSimpleMessage(true);
      gsoc_scala.Simple.internalForceInit();
      defaultInstance.initFields();
    }
    
    // @@protoc_insertion_point(class_scope:gsoc_scala.ProtobufSimpleMessage)
  }
  
  private static com.google.protobuf.Descriptors.Descriptor
    internal_static_gsoc_scala_ProtobufSimpleMessage_descriptor;
  private static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_gsoc_scala_ProtobufSimpleMessage_fieldAccessorTable;
  
  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\036src/main/protobuf/simple.proto\022\tgsoc_a" +
      "vro\"1\n\025ProtobufSimpleMessage\022\n\n\002id\030\001 \002(\005" +
      "\022\014\n\004text\030\002 \002(\t"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
      new com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
        public com.google.protobuf.ExtensionRegistry assignDescriptors(
            com.google.protobuf.Descriptors.FileDescriptor root) {
          descriptor = root;
          internal_static_gsoc_scala_ProtobufSimpleMessage_descriptor =
            getDescriptor().getMessageTypes().get(0);
          internal_static_gsoc_scala_ProtobufSimpleMessage_fieldAccessorTable = new
            com.google.protobuf.GeneratedMessage.FieldAccessorTable(
              internal_static_gsoc_scala_ProtobufSimpleMessage_descriptor,
              new java.lang.String[] { "Id", "Text", },
              gsoc_scala.Simple.ProtobufSimpleMessage.class,
              gsoc_scala.Simple.ProtobufSimpleMessage.Builder.class);
          return null;
        }
      };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
  }
  
  public static void internalForceInit() {}
  
  // @@protoc_insertion_point(outer_class_scope)
}
