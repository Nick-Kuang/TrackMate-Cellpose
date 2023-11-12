from pathlib import Path
import sys
import struct
import jax.numpy as jnp
import imageio.v2 as imageio

import lacss.deploy
from lacss.ops import patches_to_label
import lacss_pb2 as LacssMsg
import numpy as np
import typer

app = typer.Typer(pretty_exceptions_enable=False)

def read_input(st = sys.stdin.buffer):
    msg_size = st.read(4)
    msg_size = struct.unpack(">i", msg_size)[0]

    msg = LacssMsg.Input()
    msg.ParseFromString(st.read(msg_size))

    image = msg.image
    np_img = np.frombuffer(image.data, dtype=">f4").astype("float32")
    np_img = np_img.reshape(image.channel, image.width, image.height)
    np_img = np_img.transpose(2, 1, 0)

    return np_img, msg.settings

def write_result(label, st = sys.stdout.buffer):
    if len(label.shape) != 2 :
        raise ValueError(f"Expect 2D array as label. Got array of {label.shape}")

    label = np.ascontiguousarray(label, dtype=">i2") # match java format

    msg = LacssMsg.Result()
    msg.height = label.shape[0]
    msg.width = label.shape[1]
    msg.data = label.tobytes()

    assert len(msg.data) == msg.height * msg.width * 2

    msg_size_bits = struct.pack(">i", msg.ByteSize())

    st.write(msg_size_bits)
    st.write(msg.SerializeToString())

@app.command()
def main(modelpath: Path):
    model = lacss.deploy.Predictor(modelpath)
    model.detector.test_max_output = 512

    # cnt = 0

    while True:
        img, settings = read_input()
        img = img - img.min()
        img = img / img.max()

        print(f"received image {img.shape}", file=sys.stderr)

        preds = model.predict(
            img, 
            min_area=settings.min_cell_area,
            remove_out_of_bound=settings.remove_out_of_bound,
            scaling=settings.scaling,
        )

        label = patches_to_label(
            preds, 
            input_size=img.shape[:2],
            score_threshold=settings.detection_threshold,
            threshold=settings.segmentation_threshold,
        )
        
        write_result(label)

        # imageio.imwrite(f"p_{cnt}.tif", np.asarray(label))
        # cnt+=1

if __name__ == "__main__":
    app()
