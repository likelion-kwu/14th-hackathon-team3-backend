package com.example.likelionhackathon.domain.project.service;

import java.time.LocalDate;

/**
 * 프로젝트가 만들어질 때 사이클을 함께 만드는 연결부.
 *
 * <p>디자인에 사이클 생성 화면이 없는데 이슈 생성은 {@code cycleId} 를 필수로 받는다.
 * 사이클이 하나도 없으면 프로젝트를 만들어도 이슈를 만들 수 없어서,
 * 프로젝트 기간을 잘라 사이클을 미리 깔아 둔다.</p>
 *
 * <p>프로젝트가 사이클 도메인을 직접 참조하지 않도록 이 인터페이스로 끊고,
 * 사이클 도메인의 {@code CycleCreationAdapter} 가 구현한다.
 * 기간을 몇 개로 자를지, 어떻게 이름 붙일지는 사이클 쪽 사정이라 구현에 맡긴다.</p>
 */
public interface ProjectCycleCreator {

    void createInitialCycles(Long projectId, LocalDate startDate, LocalDate endDate, String goal);
}
