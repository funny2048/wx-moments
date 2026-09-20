package com.funny.moments.api;

import com.funny.framework.core.result.ApiResult;
import com.funny.moments.model.in.SeriesIn;
import com.funny.moments.model.out.SeriesOut;
import org.springframework.web.bind.annotation.GetMapping;

/**
 */
public interface SeriesApi {
    @GetMapping("/series/id")
    ApiResult<SeriesOut> getSeriesById(SeriesIn seriesRequest);
}
