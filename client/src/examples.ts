import * as fs from "fs";
import BlobCraftClient from ".";

const client = new BlobCraftClient(process.env.BLOBCRAFT_URL || "", process.env.BLOBCRAFT_ACCESS_KEY || "");

const file = "D:\\Github\\blobcraft\\client\\tmp\\example.zip";

const fileBuffer = fs.readFileSync(file);

const uploadPromise = client.uploadFileSmart(fileBuffer, "example.zip", {
  isPublic: true,
  onChunkUploaded(chunkNumber, progress) {
    console.log(`Chunk ${chunkNumber} uploaded: ${progress}%`);
  },
});

uploadPromise
  .then((response) => {
    console.log("File uploaded successfully:", response);
    console.log("File Url:", response.url);
  })

//powershell command to run this script
// node -r ts-node/register client/src/examples.ts