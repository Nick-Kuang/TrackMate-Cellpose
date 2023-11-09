#!/usr/bin/env python

#pip install --upgrade pip
#pip install --upgrade "jax[cuda12_pip]" -f https://storage.googleapis.com/jax-releases/jax_cuda_releases.html
#pip install lacss
#pip install jax==0.4.11 -f https://storage.googleapis.com/jax-releases/jax_cuda_releases.html


import typer
from pathlib import Path

#import main.resources.images.lacss as lacss
from lacss.deploy import Predictor, model_urls

from skimage.transform import rescale
import imageio.v2 as imageio

app = typer.Typer(pretty_exceptions_enable=False)


@app.command()
def main(
    modelpath: Path,
    datapath: Path = Path("../../livecell_dataset"),
    logpath: Path = Path("."),
    min_cell_area: float = 0,
    scaling_factor: float = 1,
    nms_iou: int = 0,
    segmentation_threshold: float = 0.5,
    return_label: bool = False,
    remove_out_of_bound: bool = False,
):

    path = ''#dir of files


    if modelpath == 'livecell':
        predictor = Predictor(model_urls["livecell"])
    elif modelpath == 'tissuenet':
        predictor = Predictor(model_urls["tissuenet"])
    else:
        predictor = Predictor(modelpath)

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