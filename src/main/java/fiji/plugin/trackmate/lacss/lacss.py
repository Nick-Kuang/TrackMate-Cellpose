#!/usr/bin/env python

#pip install --upgrade pip
#pip install --upgrade "jax[cuda11_pip]" -f https://storage.googleapis.com/jax-releases/jax_cuda_releases.html
#pip install lacss

import typer
from pathlib import Path

import lacss
from lacss.deploy import Predictor

from skimage.transform import rescale
import imageio.v2 as imageio

app = typer.Typer(pretty_exceptions_enable=False)


@app.command()
def main(
    modelpath: Path,
    datapath: Path = Path("../../livecell_dataset"),
    logpath: Path = Path("."),
    nms: int = 8,
    min_area: int = 0,
    min_score: float = 0.2,
    normalize: bool = True,
    dice_score: float = 0.5,
    v1_scaling: bool = False,
):

    path = ''#dir of files

    model_path = '' #cp path to model


    predictor = Predictor(model_path)

    for file in path:
        image = imageio.imread(file)
        img = rescale(image.astype("float32"), 2)
        img -= img.mean()
        img /= img.std()
        img = img[..., None]

        #pred = predictor.predict(img)
        pred_label = predictor.predict_label(img)


## Probably requires a re-formmating of output

if __name__ == "__main__":
    app()