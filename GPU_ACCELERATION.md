# GPU Acceleration

This document explains how to run the object-detection service with hardware
acceleration, covering both **NVIDIA (CUDA)** and **AMD Radeon / Intel iGPU
(OpenCL)**. It describes what must be installed on the host, how the GPU must be
exposed to the container, and how to select the acceleration backend.

All examples use placeholder values only. Do not commit environment-specific
values to the repository.

## Image variants

The published images are:

| Tag | Platform | Acceleration |
|---|---|---|
| `ghcr.io/icaroerasmo/java-object-detection:latest` | amd64 | CUDA (NVIDIA) |
| `ghcr.io/icaroerasmo/java-object-detection:latest-opencl` | amd64 | OpenCL (AMD Radeon / Intel iGPU) or CPU |
| `ghcr.io/icaroerasmo/java-object-detection:latest-arm64` | arm64 | OpenCL |

Versioned tags (`X.Y.Z`, `X.Y.Z-opencl`, `X.Y.Z-arm64`) are published alongside
each release. Pick the variant that matches the target GPU.

## Acceleration backends

The DNN models run through an inference engine selected by
`object-detection.acceleration.backend`:

| Backend | Engine | Target GPU |
|---|---|---|
| `AUTO` | auto-detect (CUDA → OpenCL → Vulkan → CPU) | any |
| `OPENCV` | OpenCV DNN on CPU | none (CPU) |
| `CUDA` | OpenCV DNN on CUDA | NVIDIA |
| `OPENCL` | OpenCV DNN on the OpenCL target | AMD Radeon / Intel iGPU |
| `VULKAN` | OpenCV DNN on Vulkan (`VKCOM`) | any Vulkan device (when compiled in) |
| `ONNX_CPU` | ONNX Runtime on CPU | none (CPU) |
| `ONNX_CUDA` | ONNX Runtime CUDA EP | NVIDIA |
| `ONNX_ROCM` | ONNX Runtime ROCm EP | none (see notes) |

The OpenCV-based targets are: `AUTO`, `CPU`, `OPENCL`, `OPENCL_FP16`, `CUDA`,
`CUDA_FP16`, `VULKAN` (`object-detection.acceleration.target`,
`face-detection-target`, `person-detection-target`).

Notes:

- `ONNX_ROCM` is accepted for configuration compatibility only. The ROCm
  execution provider was removed from ONNX Runtime 1.23+ and no Java artifact
  ships it, so this backend falls back to CPU with a warning.
- `ONNX_CUDA` requires the `onnxruntime_gpu` artifact plus CUDA 12 / cuDNN 9 on
  the host; it falls back to CPU otherwise.
- `fallback-to-cpu` (default `true`) reloads the model on CPU when the selected
  backend fails validation.

## NVIDIA (CUDA)

### Host requirements

1. Install the NVIDIA driver for your GPU.
2. Install the container toolkit so the GPU can be exposed through the CDI
   `nvidia.com/gpu` device:
   - **Ubuntu/Debian**: `nvidia-container-toolkit` (add the NVIDIA apt
     repository and run `nvidia-ctk runtime configure`).
   - **Arch Linux**: `nvidia-container-toolkit`.

   The toolkit generates `/etc/cdi/nvidia.yaml`, which defines the
   `nvidia.com/gpu` CDI device.

### Exposing the GPU to the container

```yaml
services:
  detection:
    image: ghcr.io/icaroerasmo/java-object-detection:latest
    devices:
      - nvidia.com/gpu=all
```

### Configuration

```yaml
object-detection:
  acceleration:
    backend: CUDA
    target: CUDA
```

The `nvidia.com/gpu=all` device must be present; otherwise the CUDA backend
fails validation and the service falls back to CPU (when `fallback-to-cpu` is
enabled).

## AMD Radeon / Intel iGPU (OpenCL)

Use the `latest-opencl` image, which ships the Mesa OpenCL ICD (`rusticl`) plus
the `ocl-icd` loader. OpenCL runs on the GPU through the Mesa driver.

### Host requirements

No vendor toolkit is required. The container only needs the DRM render node of
the GPU, usually `/dev/dri/renderD128`.

### Exposing the GPU to the container

```yaml
services:
  detection:
    image: ghcr.io/icaroerasmo/java-object-detection:latest-opencl
    devices:
      - /dev/dri/renderD128:/dev/dri/renderD128
```

### Configuration

```yaml
object-detection:
  acceleration:
    backend: OPENCL
    target: OPENCL
```

### Known limitation: RDNA3 (gfx1100+) kernels

The Mesa OpenCL ICD (`rusticl`) bundled in the image is tied to the `libclc`
version of the base image. Older `libclc` builds provide bitcode only up to the
GCN5 targets (`gfx906`). On RDNA3 GPUs (RX 7000 series, `gfx1100`/`gfx1101`/
`gfx1102`), kernel compilation fails with:

```text
fatal error: cannot open file '/usr/lib/clc/gfx1102-amdgcn-mesa-mesa3d.bc'
```

The service then falls back to CPU. To accelerate RDNA3 GPUs, rebuild the
`-opencl` image on a base that ships a newer Mesa/`libclc` (24.x or later, which
includes the `gfx1100+` targets). GCN5 and older GPUs (`gfx906` and below) work
with the current image.

> The host-side ROCm OpenCL runtime cannot be bind-mounted into this image: the
> image is Ubuntu-based while ROCm packages are typically built against a
> different glibc/libstdc++, which fails to load inside the container. The
> supported AMD path is the bundled `rusticl`.

## CPU (no GPU)

When no GPU is available, omit the `devices` entry and rely on the CPU backend:

```yaml
object-detection:
  acceleration:
    backend: OPENCV
    target: CPU
```

## Troubleshooting

- Confirm the device is visible inside the container:
  `ls -l /dev/dri/renderD128` (AMD) or `/dev/nvidia0` (NVIDIA).
- Startup logs report the resolved backend per model, e.g.
  `Configured object detection DNN backend=OPENCL target=OPENCL [validated]`.
  A `[fallback]` suffix means validation failed and the model was reloaded on
  CPU.
- OpenCV DNN may log `OpenCL target is not supported with current OpenCL device`
  when the OpenCL device cannot be enumerated (no ICD, no device, or an
  unsupported architecture). This indicates the GPU path is not active.
